(ns clake-tasks.script.entrypoint
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clake-tasks.specs]
    [clake-tasks.built-in :as built-in]
    [clake-tasks.api :as api])
  (:gen-class))

(defn validate-task-args
  [args cli-opts]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts)]
    (if errors
      (api/exit false (str/join "\n" errors))
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
      (let [task-name (symbol (first args))]
        (if-let [{:clake-task/keys [cli-specs] :as task-ctx} (get-task-context task-name)]
          (let [{:keys [task-opts next-arguments] :as result} (validate-task-args
                                                                (rest args)
                                                                cli-specs)]
            (if (api/exit? result)
              result
              (recur next-arguments
                     (conj tasks (assoc task-ctx :clake-task/name task-name
                                                 :clake-task/cli-opts task-opts)))))
          (api/exit false (format "Could not find task %s" task-name)))))))

(defn run-task
  [context current-task]
  (let [{task-fn       :clake-task/fn
         task-cli-opts :clake-task/cli-opts
         task-name     :clake-task/name} current-task
        config-task-opts (get-in context [:clake/config :task-opts task-name])
        task-opts (merge config-task-opts task-cli-opts)]
    (task-fn task-opts context)))

(defn execute
  [context]
  (let [tasks (parse-tasks context)]
    (if (api/exit? tasks)
      tasks
      (loop [tasks tasks]
        (if (empty? tasks)
          (api/exit true)
          (let [task (first tasks)
                result (run-task (assoc context :clake/next-tasks tasks) task)]
            (if (api/exit? result)
              result
              (recur (rest tasks)))))))))

(defn exit
  [status msg]
  (when msg (println msg))
  (System/exit status))

(defn -main
  [& args]
  (let [context (edn/read-string (first args))
        {:clake-exit/keys [message status]} (execute context)]
    (exit status message)))