(ns clake-common.script.built-in-tasks)

;; TODO: Ideally this would be auto-generated
(def default-refer-tasks
  '{repl    clake-tasks.repl/repl
    test    clake-tasks.test/test
    uberjar clake-tasks.uberjar/uberjar})

(def cli-specs
  {'clake-tasks.repl/repl [["-p" "--port PORT" "Port to start nREPL server on."
                            :parse-fn #(Integer/parseInt %)]
                           ["-l" "--lein-port" "Spit the port to .nrepl-port."]]
   'clake-tasks.test/test [["-a" "--test"]]})

(defn cli-spec
  "Returns the CLI spec for `qualified-task` from the built in CLI specs."
  [qualified-task]
  (get cli-specs qualified-task []))

(defn built-in?
  "Returns true if `qualified-task` is a built in task."
  [qualified-task]
  (some? (cli-spec qualified-task)))