(ns clake-tasks.shell-test
  (:require
    [clojure.test :refer [deftest is testing run-tests]]
    [clojure.string :as str]
    [clake-tasks.shell :as shell]))

(deftest spawn-sync-test
  (is (shell/status-success? (shell/spawn-sync "ls"))))

(deftest clojure-command-test
  (is (shell/status-success? (shell/clojure-command "--help"))))

(deftest clojure-deps-command-test
  (testing "Able to get classpath with isolated deps using no aliases"
    (let [r (shell/clojure-deps-command
              {:deps-edn '{:deps {org.clojure/clojure {:mvn/version "1.8.0"}}}
               :command  :path})]
      (is (shell/status-success? r))
      (is (str/includes? (:out r) "clojure-1.8.0"))))
  (testing "aliases work"
    (let [r (shell/clojure-deps-command
              {:deps-edn '{:deps    {org.clojure/clojure {:mvn/version "1.8.0"}}
                           :aliases {:foo {:extra-deps {org.clojure/clojure {:mvn/version "1.7.0"}}}}}
               :aliases  [:foo]
               :command  :path})]
      (is (shell/status-success? r))
      (is (str/includes? (:out r) "clojure-1.7.0"))))
  (testing "isolated works"
    (let [r (shell/clojure-deps-command
              {:deps-edn  '{:deps {org.clojure/clojure {:mvn/version "1.8.0"}}}
               :isolated? true
               :command   :path})]
      (is (shell/status-success? r))
      (is (= 2 (count (str/split (:out r) #":"))))))
  (testing "eval code"
    (let [r (shell/clojure-deps-command
              {:deps-edn  '{:deps {org.clojure/clojure {:mvn/version "1.8.0"}}}
               :eval-code ['*clojure-version*]
               :as        :edn})]
      (is (shell/status-success? r))
      (is (= {:major 1, :minor 8, :incremental 0, :qualifier nil}
             (:out r))))))