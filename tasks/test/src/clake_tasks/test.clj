(ns clake-tasks.test
  (:refer-clojure :exclude [test])
  (:require
    [clojure.string :as str]
    [clojure.test :as clj-test]
    [pjstadig.humane-test-output :as humane-test]
    [cognitect.test-runner :as test-runner]
    [clake-common.util :as util]
    [clake-common.task :as task]
    [clake-common.shell :as shell]
    [clake-common.script.built-in-tasks :as tasks]))

(defn test
  "Run the project's tests."
  {:clake/cli-specs (tasks/cli-spec `test)}
  [opts]
  (humane-test/activate!)
  (try
    (let [{:keys [fail error]} (test-runner/test opts)]
      (when (or (not= 0 fail) (not= 0 error))
        (shell/exit false)))
    (finally (shutdown-agents))))

(task/def-task-main test)