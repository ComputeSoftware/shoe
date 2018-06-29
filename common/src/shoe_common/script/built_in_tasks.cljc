(ns shoe-common.script.built-in-tasks)

;; TODO: Ideally this would be auto-generated
(def default-refer-tasks
  '{repl        shoe-tasks.repl/repl
    test        shoe-tasks.test/test
    aot         shoe-tasks.aot/aot
    libdir      shoe-tasks.libdir/libdir
    archive     shoe-tasks.archive/archive
    jar         shoe-tasks.jar/jar
    uberjar     shoe-tasks.uberjar/uberjar
    project-clj shoe-tasks.project-clj/project-clj})

(def built-in-tasks (into #{} (vals default-refer-tasks)))

(def cli-task-help-option ["-h" "--help" "Print help menu for this task."])

(defn built-in?
  "Returns true if `qualified-task` is a built in task."
  [qualified-task]
  (get built-in-tasks qualified-task))