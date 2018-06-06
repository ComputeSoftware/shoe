(ns clake-cli.core-test
  (:require
    [cljs.test :refer [deftest is testing run-tests]]
    [clake-cli.core :as cli]
    [clake-cli.io :as io]))

(defn successful-exit?
  [x]
  (= 0 (:clake-exit/status x)))

(defn failed-exit?
  [x]
  (not= 0 (:clake-exit/status x)))

(deftest resolve-clake-common-coordinate-test
  (with-redefs [cli/circle-ci-sha1 nil]
    (is (nil? (cli/resolve-clake-common-coordinate {}))))
  (is (= {:local/root "foo/common"}
         (cli/resolve-clake-common-coordinate {:local "foo"})))
  (is (= {:git/url   "https://github.com/ComputeSoftware/clake"
          :sha       "foo"
          :deps/root "common"}
         (cli/resolve-clake-common-coordinate {:sha "foo"}))))

(deftest built-in-task-deps-test
  (testing "local deps"
    (let [deps (cli/built-in-task-deps {:local ".."})]
      (is (every? io/exists? (map :local/root (vals deps))))))
  (testing "git deps"
    (let [deps (cli/built-in-task-deps {:sha "foo"})]
      (is (every? #(= "foo" %) (map :sha (vals deps)))))))

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