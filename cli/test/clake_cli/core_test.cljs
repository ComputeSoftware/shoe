(ns clake-cli.core-test
  (:require
    [cljs.test :refer [deftest is testing run-tests]]
    [clake-cli.core :as cli]))

(defn successful-exit?
  [x]
  (= 0 (:clake-exit/status x)))

(defn failed-exit?
  [x]
  (not= 0 (:clake-exit/status x)))

(deftest resolve-clake-common-coordinate-test
  (is (nil? (cli/resolve-clake-common-coordinate {})))
  (is (= {:local/root "foo"}
         (cli/resolve-clake-common-coordinate {:local "foo"})))
  (is (= {:git/url   "https://github.com/ComputeSoftware/clake"
          :sha       "foo"
          :deps/root "common"}
         (cli/resolve-clake-common-coordinate {:sha "foo"}))))

(deftest cli-test
  (testing "if clojure is not installed, fail."
    (with-redefs [cli/clj-installed? (constantly false)]
      (is (failed-exit? (cli/execute [])))))
  (testing "invalid arg fails"
    (is (failed-exit? (cli/execute ["--asdasd"]))))
  (testing "running no task fails"
    (is (successful-exit? (cli/execute []))))
  (testing "help menu is successful"
    (is (successful-exit? (cli/execute ["--help"]))))
  (testing "version is successful"
    (is (successful-exit? (cli/execute ["--version"])))))