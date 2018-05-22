(ns clake-tasks.util-test
  (:require
    [clojure.test :refer :all]
    [clake-tasks.test-util :as t]
    [clake-tasks.util :as util]))

(deftest file-name-test
  (t/with-fileset [a-edn ["a.edn" ""]]
    (is (= "a.edn" (util/file-name a-edn)))))