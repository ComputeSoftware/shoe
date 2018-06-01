(ns clake-tasks.test
  (:require
    [clojure.test :as clj-test]
    [pjstadig.humane-test-output :as humane-test]
    [clake-common.util :as util]
    [clake-common.shell :as shell]))

(defn test
  "Run the project's tests."
  {:clake/cli-specs []}
  [{:keys [aliases]} {:clake/keys [deps-edn]}]
  (humane-test/activate!)
  (let [namespaces (util/namespaces-in-project deps-edn aliases)
        ;; we need to make sure that all namespaces have been loaded
        _ (apply require namespaces)
        {:keys [fail]} (apply clj-test/run-tests namespaces)]
    (when (not= 0 fail)
      (shell/exit false))))