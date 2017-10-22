(defproject version-number-generator "1.0.0-SNAPSHOT"
  :description "Version number generator Clojure web app"
  :url "http://version-number-generator.herokuapp.com"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.6.0"]
                 [ring/ring-jetty-adapter "1.6.2"]
                 [environ "1.0.2"]
                 [org.clojure/java.jdbc "0.7.3"]
                 [org.postgresql/postgresql "42.1.4"]
                 [hiccup "1.0.5"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.4.0"]]
  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.3.1"]]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "version-number-generator-standalone.jar"
  :profiles {:production {:env {:production true}}
             :dev {:plugins [[cider/cider-nrepl "0.7.0"]
                             [lein-ancient "0.6.14"]]}})
