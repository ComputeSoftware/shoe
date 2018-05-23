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

(deftest full-deps-edn-test
  (is (map? (cli/full-deps-edn))))

(deftest aliases-from-config-test
  (let [config {:task-opts '{a {:aliases ["a-alias"]}
                             b {:aliases ["b-alias"]}
                             c {}}}]
    (is (= ["a-alias" "b-alias"]
           (cli/aliases-from-config ["a" "-b" "b" "c"] config)))
    (is (= [] (cli/aliases-from-config ["c"] config)))))

(deftest cli-test
  (testing "if clojure is not installed, fail."
    (with-redefs [cli/clj-installed? (constantly false)]
      (is (failed-exit? (cli/execute [])))))
  (testing "invalid arg fails"
    (is (failed-exit? (cli/execute ["--asdasd"]))))
  (testing "running no task fails"
    (is (failed-exit? (cli/execute []))))
  (testing "help menu is successful"
    (is (successful-exit? (cli/execute ["--help"]))))
  (testing "version is successful"
    (is (successful-exit? (cli/execute ["--version"])))))