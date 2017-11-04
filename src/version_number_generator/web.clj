(ns version-number-generator.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]]
            [clojure.java.jdbc :as db]
            [hiccup.core :as h]
            [hiccup.page :as page]
            [hiccup.element :only (link-to) :as elem]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.coercions :as coerce]
            [clojure.spec.alpha :as s]))

(defn format-appversion
  "Canonical version number"
  [v]
  (format "%d.%d.%d.%d" (:major v) (:minor v) (:build v) (:patch v)))

(defn format-appversioncode
  "Android uses the canonical version number and an appversioncode"
  [v]
  (format "%d%02d%04d%02d" (:major v) (:minor v) (:build v) (:patch v)))

(defn format-cfbundleshortversionstring
  "The iO App Store requires two different version strings
    CFBundleShortVersionString can be up to 3 non-negative integers separated by periods
    CFBundleVersion can be up to 3 non-negative integers separated by periods
    CFBundleVersion must always be incremented, but can be reset at each increment of CFBundleShortVersionString
    To match the Android and iOS versioning numbers the following version numbers are used for iOS"
  [v]
  (format "%d.%d.%d" (:major v) (:minor v) (:build v)))

(defn format-cfbundleversion
  [v]
  (format "%d" (:patch v)))

(s/def ::major (s/and int? #(<= 1 %) #(<= % 19)))
(s/def ::minor (s/and int? #(<= 0 %) #(<= % 99)))
(s/def ::build (s/and int? #(<= 0 %) #(<= % 9999)))
(s/def ::patch (s/and int? #(<= 0 %) #(<= % 99)))
(s/def ::version (s/keys :req-un [::major ::minor ::build ::patch]))

(def appversion-regex #"^[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,4}\.[0-9]{1,2}$")
(s/def ::appversion (s/and string? #(re-matches appversion-regex %)))
(def appversioncode-regex #"^[0-9]{1,2}[0-9]{2}[0-9]{4}[0-9]{2}$")
(s/def ::appversioncode (s/and string? #(re-matches appversioncode-regex %)))
(def cfbundleshortversionstring-regex #"^[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,4}}$")
(s/def ::cfbundleshortversionstring (s/and string? #(re-matches cfbundleshortversionstring-regex)))
(def cfbundleversion-regex #"^[0-9]{1,2}$")
(s/def ::appversioncode (s/and string? #(re-matches cfbundleversion-regex %)))
(def commit-regex #"^[0-9a-f]{8,40}$")
(s/def ::commit (s/and string? #(re-matches commit-regex %)))
(s/def ::new-allocation boolean?)
(s/def ::formatted-version (s/keys :req-un [::appversion ::appversioncode ::cfbundleshortversionstring ::cfbundleversion]
                                   :opt-un [::new-allocation]))

(defn format-version [version]
  ;{:pre [(s/valid? ::version version)]}
  {:appversion (format-appversion version)
   :appversioncode (format-appversioncode version)
   :cfbundleshortversionstring (format-cfbundleshortversionstring version)
   :cfbundleversion (format-cfbundleversion version)})
(s/fdef format-verison
        :args (s/cat :version ::version)
        :ret ::formatted-version)

(defn check-valid-version [v]
  (if
      (s/valid? ::version v)
    true
    (throw (IllegalArgumentException. "Version number outside range"))))

(defn record [version branch commit]
  {:pre [(s/valid? ::version version)]}
  (log/info "record" version branch commit)
  (db/insert! (env :database-url)
              :versions (merge version {:branch branch :commit commit})))

(defn find-known-version [branch commit]
  (first (db/query (env :database-url)
                [(str "select major, minor, build, patch from versions "
                      "where branch=? and commit=?")
                 branch commit])))
(s/fdef find-known-version
  :args (s/cat :branch string? :commit ::commit)
  :ret ::version)

(defn next-free-build [major minor branch]
  (let [row (first (db/query (env :database-url)
                             [(str "select build from versions "
                                   "where major=? and minor=? and branch=? "
                                   "order by build desc limit 1")
                              major minor branch]))]
    (if row
      {:major major :minor minor :build (+ (:build row) 1) :patch 0}
      {:major major :minor major :build 0 :patch 0})))
(s/fdef next-free-build
  :args (s/cat :major ::major :minor ::minor :branch ::branch)
  :ret ::version)

(defn next-free-patch [major minor branch]
  (let
      [branch-postfix (second (str/split branch #"-"))
       branch-version (zipmap [:major :minor :build] (map #(Integer/parseInt %) (str/split branch-postfix #"\." 3)))]
    (if (not (and
         (= major (:major branch-version))
         (= minor (:minor branch-version))))
      (throw (Exception. "inconsistent input")))

    (let [row (first (db/query (env :database-url)
                             [(str "select patch from versions "
                                   "where major=? and minor=? and build=? and branch=? "
                                   "order by patch desc limit 1")
                              major minor (:build branch-version) branch]))]
    (if row
      {:major major :minor minor :build (:build branch-version) :patch (+ (:patch row) 1)}
      {:major major :minor major :build (:build branch-version) :patch 0}))))
(s/fdef next-free-patch
  :args (s/cat :major ::major :minor ::minor :branch ::branch)
  :ret ::version)

(defn allocate-build-number [major minor branch]
  (if (= branch "master")
    (next-free-build major minor branch)
    (if (str/starts-with? branch "maint-")
      (next-free-patch major minor branch)
      (next-free-build major minor branch))))

(defn allocate-version [major minor branch commit can-allocate]
  (log/info "allocate-version" major minor branch commit)
  (if-let [known-version (find-known-version branch commit)]
    (do
     (log/info "found version" known-version)
     (merge (format-version known-version) {:new-allocation false}))
    (let [buildversion (allocate-build-number major minor branch)
          version {:major major :minor minor :build (buildversion :build)  :patch (buildversion :patch)}]
        (log/info "new version" buildversion)
        (check-valid-version version)
        (if can-allocate
          (do
           (record version branch commit)
           (merge (format-version version) {:new-allocation true}))
          false))))

(defn get-version [minor major branch commit can-allocate]
  (log/info "get-version" minor major branch commit)
  (if-let [version (allocate-version minor major branch commit can-allocate)]
    {:status 200
     :headers {"Content-Type" "text/plain"}
     :body (json/write-str version)}
    {:status 401
     :headers {"Content-Type" "text/plain"}
     :body ("unknown version, but no auth specified")}))

(defn get-list [branch commit]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (concat
          (for [selected (db/query (env :database-url)
                            [(str "select major, minor, build, patch, branch, commit from versions "
                                  "order by major desc, minor desc, build desc, patch desc, branch")])]
              (format "%s %s %s<br />" (format-appversion selected) (:branch selected) (:commit selected))))})

(defn usage []
  (let [host "https://version-number-generator.herokuapp.com"
        sample-1 "/v1/version?major=3&minor=0&branch=master&commit=deadbee"
        sample-2 "/v1/version?major=3&minor=0&branch=maint-3.0.1400&commit=deadbeef2"
        sample-3 "/v1/version?major=3&minor=0&branch=torstein/break-stuff&commit=deadbeef3"
        sample-4 "/versions"]
    (page/html5
     [:head
      [:title "Version number generator"]]
     [:body
      [:div {:id "header"} [:h1 "Version number generator"]]
      [:div {:id "usage"} [:h2 "Usage:"]
       [:p (elem/link-to sample-1 (str host sample-1))]
       [:p (elem/link-to sample-2 (str host sample-2))]
       [:p (elem/link-to sample-3 (str host sample-3))]
       [:p (elem/link-to sample-4 (str host sample-4))]]])))

(defn db-schema-migrated?
  "Check if the schema has been migrated to the database"
  []
  (-> (db/query (env :database-url)
                [(str "select count(*) from information_schema.tables "
                      "where table_name='versions'")])
      first :count pos?))

(defn apply-schema-migration
  "Apply the schema to the database"
  []
  (when (not (db-schema-migrated?))
    (db/db-do-commands (env :database-url)
                       (db/create-table-ddl
                        :versions
                        [[:id :serial "PRIMARY KEY"]
                         [:major :int]
                         [:minor :int]
                         [:build :int]
                         [:patch :int]
                         [:branch :text]
                         [:commit :text]
                         [:created_at :timestamp
                          "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]]))
    (record {:major 3 :minor 0 :build 1000 :patch  0} "master",        "unknown")
    (record {:major 3 :minor 0 :build  900 :patch 10} "maint-3.0.900", "unknown")))

(def secret "") ;; TODO: secreter secret

(defn is-authenticated [headers]
  (let [auth (str (:authentication headers))]
    (= auth secret)))

(defn keywordize-map [my-map]
  (into {}
        (for [[k v] my-map]
          [(keyword k) v])))

(defroutes app
  (GET "/:api-version/version" [api-version
                                major :<< coerce/as-int
                                minor :<< coerce/as-int
                                branch
                                commit
                                :as { headers :headers }]

       (let [authenticated (is-authenticated (keywordize-map headers))]
         (log/info "is-authenticated" authenticated)
         (condp = api-version
           "v1" (get-version major minor branch commit authenticated)
           (route/not-found (slurp (io/resource "404.html")))))
       )
  (GET "/versions" {{branch :branch commit :commit} :params}
       (get-list branch commit))
  (GET "/" []
       (usage))
  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn -main [& [port]]
  (apply-schema-migration)
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))

;; For interactive development:
;; (require 'version-number-generator.web)
;; (.stop server)
;; (def server (version-number-generator.web/-main))
