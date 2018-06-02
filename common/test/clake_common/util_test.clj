(ns clake-common.util-test
  (:require
    [clojure.test :refer :all]
    [clake-common.util :as util]))

(deftest symbol-from-var-test
  (is (= 'clojure.core/conj (util/symbol-from-var #'conj))))