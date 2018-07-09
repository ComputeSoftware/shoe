(ns shoe-common.script.entrypoint
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.tools.cli :as cli]
    [shoe-common.shell :as shell]
    [shoe-common.task :as task]
    [shoe-common.script.built-in-tasks :as tasks])
  (:import (java.nio.file Paths)
           (java.io FileNotFoundException)))

(defn lookup-task-cli-specs
  "Returns the CLI spec for `task-name-str` if it is available."
  [qualified-task]
  (try
    (require (symbol (namespace qualified-task)))
    (if-let [v (resolve qualified-task)]
      (conj (:shoe/cli-specs (meta v)) tasks/cli-task-help-option)
      (task/exit false (str "Could not resolve task " qualified-task ".")))
    (catch FileNotFoundException ex
      (task/exit false (format "Could not require %s. Is it on the classpath?" (namespace qualified-task))))))

;; we need to validate the CLI opts at entrypoint to separate out the task calls
(defn validate-task-cli-opts
  [args cli-specs]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-specs :in-order true)]
    (cond
      errors (task/exit false (str/join \n errors))
      :else arguments)))

(defn built-in-task-coord
  "Returns the coordinate for the built-in task with the qualified name
  `qualified-task`."
  [qualified-task common-dep]
  (let [task-dep-name (symbol (str "shoe.tasks/" (name qualified-task)))]
    {task-dep-name
     (cond
       (:local/root common-dep)
       {:local/root (.getAbsolutePath
                      ;; we are passed the location of the /common folder
                      ;; and need to determine the absolute path for the root
                      ;; of the project
                      (io/file (-> (:local/root common-dep)
                                   (Paths/get (make-array String 0))
                                   (.toAbsolutePath)
                                   (.normalize)
                                   (.toFile)
                                   (.getParentFile)
                                   (.getAbsolutePath))
                               "tasks"
                               (name qualified-task)))}
       (:git/url common-dep)
       {:git/url   "https://github.com/ComputeSoftware/shoe"
        :deps/root (str "tasks/" (name qualified-task))
        :sha       (:sha common-dep)})}))

(defn task-clojure-command
  [qualified-task cli-args extra-deps aliases]
  ;; we cant simply eval (qualified-task parsed-cli-opts) because the parsed CLI
  ;; opts may not be serializable as a string. Instead we parse the CLI opts at
  ;; runtime a second time and then call the function.
  {:aliases   aliases
   :deps-edn  {:deps
               (if (tasks/built-in? qualified-task)
                 (let [common-dep (get extra-deps 'shoe/common)]
                   (merge extra-deps (built-in-task-coord qualified-task common-dep)))
                 extra-deps)}
   :eval-code `[(require 'shoe-common.task)
                (require '~(symbol (namespace qualified-task)))
                (shoe-common.task/execute-task-handler
                  '~qualified-task
                  ~cli-args)]
   :cmd-opts  {:stdio ["inherit" "inherit" "inherit"]}})

(defn parse-cli-args
  "Returns a vector of maps with keys :task and :args. :task is a qualified symbol
  of the task function to run. :args is a vector of command line arguments that
  were passed to the task."
  [config args]
  (loop [args args
         cmds []]
    (if-not (empty? args)
      (let [task-name-str (first args)]
        (if-let [qualified-task (task/qualify-task config (symbol task-name-str))]
          (let [args-without-task (rest args)
                cli-specs (lookup-task-cli-specs qualified-task)]
            (if-not (task/exit? cli-specs)
              (let [next-args-or-exit (validate-task-cli-opts args-without-task cli-specs)]
                (if-not (task/exit? next-args-or-exit)
                  (recur next-args-or-exit (conj cmds {:task qualified-task
                                                       :args (vec (take (- (count args-without-task)
                                                                           (count next-args-or-exit))
                                                                        args-without-task))}))
                  next-args-or-exit))
              cli-specs))
          (task/exit false (str "Could not resolve task " task-name-str "."))))
      cmds)))

(defn execute
  [{:keys [extra-deps args aliases]}]
  (let [config (task/load-config)
        parsed-args (parse-cli-args config args)]
    (if-not (task/exit? parsed-args)
      ;; loop to run the commands parsed from the cli args
      (loop [parsed-args parsed-args]
        (if-not (empty? parsed-args)
          (let [{:keys [task args]} (first parsed-args)
                result (shell/clojure-deps-command (task-clojure-command task args extra-deps aliases))]
            ;; stop task execution if one of the tasks fails with a non-zero exit
            (if (shell/status-success? result)
              (recur (rest parsed-args))
              (task/exit (:exit result)
                         (if (shell/classpath-error? result)
                           (str "Error building classpath for task " task ".\n"
                                (:err result))
                           (:err result)))))
          (task/exit true)))
      parsed-args)))

(defn -main
  [& args]
  (let [init-opts (edn/read-string (first args))
        result (execute init-opts)]
    (shell/system-exit result)))