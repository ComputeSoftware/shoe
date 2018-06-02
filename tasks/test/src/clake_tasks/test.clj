(ns clake-tasks.test
  (:refer-clojure :exclude [test])
  (:require
    [clojure.string :as str]
    [clojure.test :as clj-test]
    [pjstadig.humane-test-output :as humane-test]
    [clake-common.util :as util]
    [clake-common.task :as task]
    [clake-common.shell :as shell]))

(defn test
  "Run the project's tests."
  {:clake/cli-specs [["-a" "--aliases VALS" ""
                      :parse-fn (fn [vals]
                                  (str/split vals #","))]]}
  [{:keys [aliases]}]
  (humane-test/activate!)
  (let [deps-edn (shell/full-deps-edn (shell/deps-config-files))
        namespaces (util/namespaces-in-project deps-edn aliases)
        ; we need to make sure that all namespaces have been loaded
        _ (apply require namespaces)
        {:keys [fail]} (apply clj-test/run-tests namespaces)]
    (when (not= 0 fail)
      (shell/system-exit false))))

(task/def-task-main test)