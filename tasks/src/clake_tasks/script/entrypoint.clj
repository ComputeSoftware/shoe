(ns clake-tasks.script.entrypoint
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clake-tasks.built-in :as built-in]))

(defn validate-task-args
  [args cli-opts]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)]
    (if errors
      {:exit-message (str/join "\n" errors)}
      {:task-opts options})))

(defn task-vars-in-ns
  "Returns a list of vars that represent task functions in `ns-sym`."
  [ns-sym]
  (filter #(:clake/cli-opts (meta %)) (vals (ns-publics ns-sym))))

(def built-in-tasks
  "Map of a symbol to a var for all tasks in the built-in namespace."
  (reduce (fn [name->var var]
            (assoc name->var (:name (meta var)) var))
          {} (task-vars-in-ns 'clake-tasks.built-in)))

(defn load-task
  [config task-name args-str]
  (if (contains? built-in-tasks task-name)
    (let [task-var (get built-in-tasks task-name)
          cli-opts (:clake/cli-opts (meta task-var))
          validate-args-result (validate-task-args args-str cli-opts)]
      (if (:exit-message validate-args-result)
        validate-args-result
        (@task-var (:task-opts validate-args-result))))
    {:exit-message "Command not found." :ok? false}))

(defn validate-task
  [{:keys [config task-name task-args]}]
  (load-task config (symbol task-name) task-args))

(defn exit
  [status msg]
  (println msg)
  ;; this is not used right now
  {:status status})

;; TODO: need a way for tasks to cleanup after themselves...
(defn add-shutdown-hook
  []
  (.addShutdownHook (Runtime/getRuntime) (Thread. (fn []
                                                    ))))

(defn -main
  [& args]
  (let [data (edn/read-string (first args))
        {:keys [exit-message ok?]} (validate-task data)]
    (exit (if ok? 0 1) exit-message)))