(ns clake-tasks.script.entrypoint
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clake-tasks.specs]
    [clake-tasks.built-in :as built-in]
    [clake-tasks.api :as api]
    [clake-tasks.log :as log]
    [clake-tasks.util :as util])
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

(def default-refer-tasks
  (reduce (fn [refer-tasks task-var]
            (let [qualified-sym (util/symbol-from-var task-var)]
              (assoc refer-tasks (symbol (name qualified-sym)) qualified-sym)))
          {} (task-vars-in-ns 'clake-tasks.built-in)))

(defn get-task-context
  "Returns a map of `:task-fn` and `:task-cli-opts` given a `task-name`."
  [qualified-task-name]
  (when qualified-task-name
    (let [task-var (resolve qualified-task-name)
          cli-specs (:clake/cli-specs (meta task-var))]
      {:clake-task/fn             @task-var
       :clake-task/cli-specs      cli-specs
       :clake-task/name           (symbol (name qualified-task-name))
       :clake-task/qualified-name qualified-task-name})))

(defn parse-tasks
  [{:clake/keys [config task-cli-args]}]
  (loop [args task-cli-args
         tasks []]
    (if (empty? args)
      tasks
      (let [task-name (symbol (first args))]
        (if-let [qualified-task-name (get-in config [:refer-tasks task-name])]
          (if-let [{:clake-task/keys [cli-specs] :as task-ctx} (get-task-context qualified-task-name)]
            (let [{:keys [task-opts next-arguments] :as result} (validate-task-args (rest args) cli-specs)]
              (if (api/exit? result)
                result
                (recur next-arguments
                       (conj tasks (assoc task-ctx :clake-task/cli-opts task-opts)))))
            (api/exit false (format "Could not find task %s data" task-name)))
          (api/exit false (format "Could not find task %s" task-name)))))))

(defn run-task
  [context current-task]
  (let [{task-fn :clake-task/fn} current-task]
    (task-fn nil context)))

(defn merge-default-context
  [context]
  (update-in context [:clake/config :refer-tasks] (fn [refer-map] (merge default-refer-tasks refer-map))))

(defn execute
  [context]
  (let [context (merge-default-context context)
        tasks (parse-tasks context)
        context (assoc context :clake/tasks tasks)]
    (if (api/exit? tasks)
      tasks
      (loop [tasks tasks]
        (if (empty? tasks)
          (api/exit true)
          (let [task (first tasks)
                next-tasks (rest tasks)
                result (run-task (assoc context :clake/next-tasks next-tasks) task)]
            ;; if a task returns an exit map, stop task execution
            (if (api/exit? result)
              result
              (recur next-tasks))))))))

(defn exit
  [status msg]
  (when msg
    (if (= status 0)
      (log/info msg)
      (log/error msg)))
  (System/exit status))

(defn -main
  [& args]
  (let [context (edn/read-string (first args))
        {:clake-exit/keys [message status]} (execute context)]
    (exit status message)))