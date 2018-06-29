(ns shoe-tasks.test
  (:refer-clojure :exclude [test])
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [pjstadig.humane-test-output :as humane-test]
    [cognitect.test-runner :as test-runner]
    [shoe-common.task :as task]))

(defn- parse-kw
  [s]
  (if (str/starts-with? s ":") (edn/read-string s) (keyword s)))

(defn- accumulate
  [m k v]
  (update-in m [k] (fnil conj #{}) v))

(defn test
  "Run the project's tests."
  {:shoe/cli-specs [["-d" "--dir DIRNAME" "Name of the directory containing tests. Defaults to \"test\"."
                      :parse-fn str
                      :assoc-fn accumulate]
                     ["-n" "--namespace SYMBOL" "Symbol indicating a specific namespace to test."
                      :parse-fn symbol
                      :assoc-fn accumulate]
                     ["-r" "--namespace-regex REGEX" "Regex for namespaces to test. Defaults to #\".*-test$\"\n(i.e, only namespaces ending in '-test' are evaluated)"
                      :parse-fn re-pattern
                      :assoc-fn accumulate]
                     ["-v" "--var SYMBOL" "Symbol indicating the fully qualified name of a specific test."
                      :parse-fn symbol
                      :assoc-fn accumulate]
                     ["-i" "--include KEYWORD" "Run only tests that have this metadata keyword."
                      :parse-fn parse-kw
                      :assoc-fn accumulate]
                     ["-e" "--exclude KEYWORD" "Exclude tests with this metadata keyword."
                      :parse-fn parse-kw
                      :assoc-fn accumulate]]}
  [opts]
  (humane-test/activate!)
  (try
    (let [{:keys [fail error]} (test-runner/test opts)]
      (when (or (not= 0 fail) (not= 0 error))
        (task/exit false)))
    (finally (shutdown-agents))))

(task/def-task-main test)