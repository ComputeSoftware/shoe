(ns clake-tasks.script.entrypoint
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clake-tasks.built-in :as built-in]))

(defn exit?
  [x]
  (some? (:exit-message x)))

(defn validate-task-args
  [args cli-opts]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)]
    (if errors
      {:exit-message (str/join "\n" errors) :ok? false}
      {:task-opts      options
       :next-arguments arguments})))

(defn task-vars-in-ns
  "Returns a list of vars that represent task functions in `ns-sym`."
  [ns-sym]
  (filter #(:clake/cli-specs (meta %)) (vals (ns-publics ns-sym))))

(def built-in-tasks
  "Map of a symbol to a var for all tasks in the built-in namespace."
  (reduce (fn [name->var var]
            (assoc name->var (:name (meta var)) var))
          {} (task-vars-in-ns 'clake-tasks.built-in)))

(defn get-task-context
  "Returns a map of `:task-fn` and `:task-cli-opts` given a `task-name`."
  [task-name]
  (when task-name
    (let [task-name (symbol task-name)]
      (if (contains? built-in-tasks task-name)
        (let [task-var (get built-in-tasks task-name)
              cli-specs (:clake/cli-specs (meta task-var))]
          {:clake-task/fn        @task-var
           :clake-task/cli-specs cli-specs})
        ;; implement custom tasks here
        nil))))

(defn parse-tasks
  [{:clake/keys [config task-cli-args]}]
  (loop [args task-cli-args
         tasks []]
    (if (empty? args)
      tasks
      (let [task-name (first args)]
        (if-let [{:clake-task/keys [cli-specs] :as task-ctx} (get-task-context task-name)]
          (let [{:keys [task-opts next-arguments] :as result} (validate-task-args
                                                                (rest args)
                                                                cli-specs)]
            (if (exit? result)
              result
              (recur next-arguments
                     (conj tasks (assoc task-ctx :clake-task/cli-opts task-opts)))))
          {:exit-message (format "Could not find task %s" task-name)
           :ok?          false})))))

(defn run-task
  [context current-task]
  (let [{task-fn   :clake-task/fn
         task-opts :clake-task/cli-opts} current-task]
    (task-fn task-opts context)))

(defn execute
  [context]
  (let [tasks (parse-tasks context)]
    ;(prn tasks)
    (if (exit? tasks)
      tasks
      (do
        (loop [tasks tasks]
          (if (empty? tasks)
            nil
            (let [task (first tasks)
                  _ (run-task (assoc context :clake/next-tasks tasks) task)]
              (recur (rest tasks)))))
        {:exit-message nil :ok? true}))))

(defn exit
  [status msg]
  (when msg (println msg))
  ;; this is not used right now
  {:status status})

(defn -main
  [& args]
  (let [context (edn/read-string (first args))
        {:keys [exit-message ok?]} (execute context)]
    (exit (if ok? 0 1) exit-message)))