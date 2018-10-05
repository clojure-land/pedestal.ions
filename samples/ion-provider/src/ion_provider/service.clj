(ns ion-provider.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.log :as log]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.interceptor :as interceptor]
            [com.cognitect.pedestal.ions :as provider]
            [ion-provider.datomic]
            [ring.util.response :as ring-resp]
            [datomic.client.api :as d]))

(def get-client
  "This function will return a local implementation of the client
  interface when run on a Datomic compute node. If you want to call
  locally, fill in the correct values in the map."
  (memoize (fn [app-name]
             (let [region (System/getenv "AWS_REGION")]
               (d/client {:server-type :ion
                          :region      region
                          :system      app-name
                          :query-group app-name
                          :endpoint    (format "http://entry.%s.%s.datomic.net:8182/" app-name region)
                          :proxy-port  8182})))))

(defn about
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about))))

(defn home
  [request]
  (ring-resp/response "Hello World!"))

(defn- get-connection
  "Returns a datomic connection.
  Ensures the db is created and schema is loaded."
  [app-name db-name]
  (let [client (get-client app-name)]
    (d/create-database client {:db-name db-name})
    (let [conn (d/connect client {:db-name db-name})]
      (ion-provider.datomic/load-dataset conn)
      conn)))

(defn- get-in-or-throw
  [m ks]
  (if-let [v (get-in m ks)]
    v
    (let [msg (format "Value not found for keys %s" ks)]
      (log/error :msg msg :keys ks :m m)
      (throw (ex-info msg {})))))

(def datomic-interceptor
  (interceptor/interceptor
   {:name ::datomic-interceptor
    :enter (fn [ctx]
             (let [app-name (get-in-or-throw ctx [::provider/app-info :app-name])
                   db-name (get-in-or-throw ctx [::provider/params :db-name])
                   conn (get-connection app-name db-name)
                   m    {::conn conn
                         ::db   (d/db conn)}]
               (-> ctx
                   (merge m)
                   (update-in [:request] merge m))))}))

(defn pets
  [request]
  (let [db (::db request)]
    (ring-resp/response
     (map (comp #(dissoc % :db/id) first)
          (d/q '[:find (pull ?e [*])
                 :where [?e :pet-store.pet/id]]
               db)))))

(def pet-interceptor
  (interceptor/interceptor
   {:name ::pet-interceptor
    :enter (fn [ctx]
             (let [db (::db ctx)
                   id (long (Integer/valueOf (or (get-in ctx [:request :path-params :id])
                                                 (get-in ctx [:request :json-params :id]))))
                   e  (d/pull db '[*] [:pet-store.pet/id id])]
               (assoc-in ctx [:request ::pet] (dissoc e :db/id))))}))

(defn get-pet
  [request]
  (let [pet (::pet request)]
    (when (seq pet)
      (ring-resp/response pet))))

(defn add-pet
  [request]
  (let [conn                  (::conn request)
        pet                   (::pet request)
        {:keys [id name tag]} (:json-params request)]
    (if (seq pet)
      (ring-resp/status (ring-resp/response (format "Pet with id %d exists." id)) 500)
      (do
        (d/transact conn {:tx-data [{:db/id              "new-pet"
                                     :pet-store.pet/id   (long id)
                                     :pet-store.pet/name name
                                     :pet-store.pet/tag  tag}]})
        (ring-resp/status (ring-resp/response "Created") 201)))))

(defn update-pet
  [request]
  (let [conn               (::conn request)
        pet                (::pet request)
        id                 (Long/valueOf (get-in request [:path-params :id]))
        {:keys [name tag]} (:json-params request)]
    (when (seq pet)
      (let [{:keys [db-after]} (d/transact conn {:tx-data [{:db/id              [:pet-store.pet/id id]
                                                            :pet-store.pet/id   id
                                                            :pet-store.pet/name name
                                                            :pet-store.pet/tag  tag}]})]
        (ring-resp/response (dissoc (d/pull db-after '[*] [:pet-store.pet/id id]) :db/id))))))

(defn remove-pet
  [request]
  (let [conn (::conn request)
        pet  (::pet request)]
    (when (seq pet)
      (d/transact conn {:tx-data [[:db/retractEntity [:pet-store.pet/id (:pet-store.pet/id pet)]]]})
      (ring-resp/status (ring-resp/response "No Content.") 204))))

(def common-interceptors [(body-params/body-params) http/json-body])

(def app-interceptors (into [datomic-interceptor] common-interceptors))

;; Tabular routes
(def routes #{["/" :get (conj common-interceptors `home)]
              ["/about" :get (conj common-interceptors `about)]
              ["/pets" :get (conj app-interceptors `pets)]
              ["/pets" :post (into app-interceptors [pet-interceptor `add-pet])]
              ["/pet/:id" :get (into app-interceptors [pet-interceptor `get-pet])]
              ["/pet/:id" :put (into app-interceptors [pet-interceptor `update-pet])]
              ["/pet/:id" :delete (into app-interceptors [pet-interceptor `remove-pet])]})

;; See http/default-interceptors for additional options you can configure
(def service {;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/chain-provider provider/ion-provider})
