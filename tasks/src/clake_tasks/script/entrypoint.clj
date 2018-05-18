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
  (filter #(:clake/cli-opts (meta %)) (vals (ns-publics ns-sym))))

(def built-in-tasks
  "Map of a symbol to a var for all tasks in the built-in namespace."
  (reduce (fn [name->var var]
            (assoc name->var (:name (meta var)) var))
          {} (task-vars-in-ns 'clake-tasks.built-in)))

(defn get-task
  "Returns a map of `:task-fn` and `:task-cli-opts` given a `task-name`."
  [task-name]
  (when task-name
    (let [task-name (symbol task-name)]
      (if (contains? built-in-tasks task-name)
        (let [task-var (get built-in-tasks task-name)
              cli-opts (:clake/cli-opts (meta task-var))]
          {:task-fn       @task-var
           :task-cli-opts cli-opts})
        ;; implement custom tasks here
        nil))))

(defn parse-tasks
  [{:keys [config args]}]
  (loop [args args
         tasks []]
    (if (empty? args)
      tasks
      (let [task-name (first args)]
        (if-let [{:keys [task-cli-opts] :as task-map} (get-task task-name)]
          (let [{:keys [task-opts next-arguments] :as result} (validate-task-args
                                                                (rest args)
                                                                task-cli-opts)]
            (if (exit? result)
              result
              (recur next-arguments (conj tasks (assoc task-map :task-opts task-opts)))))
          {:exit-message (format "Could not find task %s" task-name)
           :ok?          false})))))

(defn run-task
  [config {:keys [task-fn task-opts]}]
  (task-fn task-opts))

(defn execute
  [data]
  (let [tasks (parse-tasks data)]
    (if (exit? tasks)
      tasks
      (do
        (doseq [task tasks]
          (run-task (:config data) task))
        {:exit-message nil :ok? true}))))

(defn exit
  [status msg]
  (when msg (println msg))
  ;; this is not used right now
  {:status status})

(defn -main
  [& args]
  (let [data (edn/read-string (first args))
        {:keys [exit-message ok?]} (execute data)]
    (exit (if ok? 0 1) exit-message)))