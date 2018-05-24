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

(deftest resolve-deps-edn-paths-test
  (testing "able to resolve keywords"
    (let [paths (cli/resolve-deps-edn-paths [:install :user :project])]
      (is (= 3 (count paths)))
      (is (every? string? paths))))
  (testing "string paths are left alone"
    (is (= ["deps.edn"] (cli/resolve-deps-edn-paths ["deps.edn"])))))

(deftest full-deps-edn-test
  (is (map? (cli/full-deps-edn ["deps.edn"]))))

(deftest aliases-from-config-test
  (let [config {:task-opts '{a {:aliases ["a-alias"]}
                             b {:aliases ["b-alias"]}
                             c {}}}]
    (is (= ["a-alias" "b-alias"]
           (cli/aliases-from-config ["a" "-b" "b" "c"] config)))
    (is (= [] (cli/aliases-from-config ["c"] config)))))

(defn get-Sdeps-value
  "Returns the value of -Sdeps in `args-vec`."
  [args-vec]
  (let [Sdeps-index (ffirst (filter (fn [[_ x]] (= x "-Sdeps"))
                                    (map-indexed vector args-vec)))]
    (get args-vec (inc Sdeps-index))))

(deftest run-task-command-test
  (testing "able to set deps.edn via CLI args"
    (letfn [(run-cmd [deps-path]
              (-> ["-d" deps-path "test"]
                  (cli/validate-args)
                  (cli/run-task-command)
                  :args
                  get-Sdeps-value))]
      (is (= (run-cmd "deps.edn")
             (run-cmd ":project"))))))

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