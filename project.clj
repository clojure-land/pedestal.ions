; Copyright 2013 Relevance, Inc.
; Copyright 2014-2018 Cognitect, Inc.

; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0)
; which can be found in the file epl-v10.html at the root of this distribution.
;
; By using this software in any fashion, you are agreeing to be bound by
; the terms of this license.
;
; You must not remove this notice, or any other, from this software.

(defproject io.pedestal/pedestal.ions "0.1.0-SNAPSHOT"
  :description "Interceptor chain provider for Datomic ions"
  :url "https://github.com/pedestal/pedestal"
  :scm "https://github.com/pedestal/pedestal"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [io.pedestal/pedestal.interceptor "0.5.4"]
                 [io.pedestal/pedestal.log "0.5.4"]]

  :min-lein-version "2.0.0"
  :global-vars {*warn-on-reflection* true}
  :pedantic? :abort

  :aliases {"docs" ["with-profile" "docs" "codox"]}
  :profiles {:provided {:dependencies [[com.datomic/ion "0.9.26" :exclusions [commons-logging]]]}
             :dev {:dependencies [[io.pedestal/pedestal.service "0.5.4" :exclusions [joda-time]]
                                  [javax.servlet/javax.servlet-api "3.1.0"]
                                  [com.datomic/ion-dev "0.9.176" :exclusions [org.clojure/clojure
                                                                              commons-logging
                                                                              org.apache.httpcomponents/httpcore]]]}
             :docs {:pedantic? :ranges
                    :dependencies [[ring/ring-core "1.6.3"]]
                    :plugins   [[lein-codox "0.9.5"]]}})
