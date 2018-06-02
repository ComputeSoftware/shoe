(ns clake-common.script.entrypoint
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]
    [clojure.tools.cli :as cli]
    [clake-common.shell :as shell]
    [clake-common.script.built-in-tasks :as tasks]))

(defn qualify-task
  "Returns a qualified symbol pointing to the task function or `nil` if
  `task-name` could not be qualified. First tries to qualify the task via a
  `config` lookup. If that fails then tries to lookup in built-in map."
  [config task-name]
  (if (qualified-symbol? task-name)
    task-name
    (or (get-in config [:refer-tasks task-name])
        (get tasks/default-refer-tasks task-name))))

(defn lookup-task-cli-specs
  "Returns the CLI spec for `task-name-str` if it is available."
  [qualified-task]
  (or (get tasks/cli-specs qualified-task)
      (when-let [v (resolve qualified-task)]
        (:clake/cli-specs (meta v)))))

(defn validate-task-cli-opts
  [config args cli-specs]
  ;; TODO: merge in a default help menu here
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-specs :in-order true)]
    (cond
      errors (shell/exit false (str/join \n errors))
      :else arguments)))

(defn task-clojure-command
  [qualified-task cli-args clake-sha]
  ;; we cant simply eval (qualified-task parsed-cli-opts) because the parsed CLI
  ;; opts may not be serializable as a string. Instead we parse the CLI opts at
  ;; runtime a second time and then call the function.
  {:deps      (if (tasks/built-in? qualified-task)
                {:deps {(symbol (str "clake-tasks." (name qualified-task)))
                        {:git/url   "https://github.com/ComputeSoftware/clake"
                         :deps/root (str "tasks/" (name qualified-task))
                         :sha       clake-sha}}}
                {})
   :eval-code `[(require 'clake-common.task)
                (require ~(symbol (namespace qualified-task)))
                (let [v# (resolve ~qualified-task)]
                  (clake-common.task/execute-task-handler
                    ~qualified-task
                    (:clake/cli-specs (meta v#))
                    ~cli-args
                    @v#))]})

(defn parse-cli-args
  "Returns a vector of maps with keys :task and :args. :task is a qualified symbol
  of the task function to run. :args is a vector of command line arguments that
  were passed to the task."
  [config args]
  (loop [args args
         cmds []]
    (if-not (empty? args)
      (let [task-name-str (first args)]
        (if-let [qualified-task (qualify-task config (symbol task-name-str))]
          (let [args-without-task (rest args)
                task-cli-specs (lookup-task-cli-specs qualified-task)
                next-args-or-exit (validate-task-cli-opts config args-without-task task-cli-specs)]
            (if-not (shell/exit? next-args-or-exit)
              (recur next-args-or-exit (conj cmds {:task qualified-task
                                                   :args (vec (take (- (count args-without-task)
                                                                       (count next-args-or-exit))
                                                                    args-without-task))}))
              next-args-or-exit))
          (shell/exit false (str "Could not resolve task " task-name-str "."))))
      cmds)))

(defn execute
  [args]
  (let [config {}
        parsed-args (parse-cli-args config args)]
    (loop [parsed-args parsed-args]
      (if-not (empty? parsed-args)
        (let [{:keys [task args]} (first parsed-args)
              result (shell/clojure-deps-command (task-clojure-command task args))]
          (if-not (shell/exit? result)
            (recur (rest parsed-args))
            result))
        (shell/exit true)))))

(defn -main
  [& args]
  (prn (edn/read-string (first args)))
  #_(shell/system-exit (execute args)))