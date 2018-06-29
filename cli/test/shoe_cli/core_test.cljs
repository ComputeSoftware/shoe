(ns shoe-cli.core-test
  (:require
    [cljs.test :refer [deftest is testing run-tests]]
    [shoe-cli.core :as cli]
    [shoe-cli.io :as io]))

(defn successful-exit?
  [x]
  (= 0 (:shoe-exit/status x)))

(defn failed-exit?
  [x]
  (not= 0 (:shoe-exit/status x)))

(deftest resolve-shoe-common-coordinate-test
  (with-redefs [cli/circle-ci-sha1 nil]
    (is (nil? (cli/resolve-shoe-common-coordinate {}))))
  (is (= {:local/root "foo/common"}
         (cli/resolve-shoe-common-coordinate {:local "foo"})))
  (is (= {:git/url   "https://github.com/ComputeSoftware/shoe"
          :sha       "foo"
          :deps/root "common"}
         (cli/resolve-shoe-common-coordinate {:sha "foo"}))))

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