(ns version-number-generator.web-test
  (:require [clojure.test :refer :all]
            [version-number-generator.web :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.test.alpha :as stest]
            [clojure.spec.gen.alpha :as gen]))

(def some-version {:major 3 :minor 0 :build 100 :patch 1})
(def high-version {:major 20 :minor 99 :build 8000 :patch 99})
(def invalid-version {:major -2 :minor "99" :build 8000 :patch false})

(deftest can-format-appversion
  (is (= "3.0.100.1" (format-appversion some-version)))
  (is (= "20.99.8000.99" (format-appversion high-version))))

(deftest can-format-appversioncode
  (is (=  "300010001" (format-appversioncode some-version)))
  (is (= "2099800099" (format-appversioncode high-version))))

(deftest can-format-cfbundleshortversionstring
  (is (= "3.0.100" (format-cfbundleshortversionstring some-version)))
  (is (= "20.99.8000" (format-cfbundleshortversionstring high-version))))

(deftest can-format-cfbundleversion
  (is (= "1" (format-cfbundleversion some-version)))
  (is (= "99" (format-cfbundleversion high-version))))

(deftest catches-invalid-version-format
  (is (= true (check-valid-version some-version)))
  (is (thrown? IllegalArgumentException (check-valid-version {:major 3 :minor 0 :build 100 :patch -1})))
  (is (thrown? IllegalArgumentException (check-valid-version {:major 22 :minor 0 :build 100 :patch 0})))
  (is (thrown? IllegalArgumentException (check-valid-version {:major 22 :minor 102 :build 100 :patch 0}))))

(stest/instrument `format-version)

(deftest can-format-version
  (is (=
       {:appversion "3.0.100.1"
        :appversioncode "300010001"
        :cfbundleshortversionstring "3.0.100"
        :cfbundleversion "1"})
      (format-version some-version)))

(deftest check-specs
  (is (stest/check `format-version))
  (is (stest/check))
  ;;(is (s/exercise-fn `format-version))
  )
