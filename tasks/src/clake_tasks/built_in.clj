(ns clake-tasks.built-in
  (:require
    [clojure.main :as main]
    [clojure.tools.nrepl.server :as nrepl-server]
    [clake-tasks.api :as api]))

(api/deftask nrepl
  "Task that starts an nREPL server.

  Note: This is a blocking task."
  {:clake/cli-opts [["-p" "--port PORT" "Port to start nREPL server on."
                     :default 0
                     :parse-fn #(Integer/parseInt %)]
                    ["-l" "--lein-port" "Spit the port to .nrepl-port."]]}
  [{:keys [port lein-port] :as task-opts}]
  (let [#_#_repl (main/repl)
        server (nrepl-server/start-server :port port)
        selected-port (:port server)]
    (println (format "nREPL server started on port %s" selected-port))
    (when lein-port
      (spit ".nrepl-port" selected-port))
    @(promise)))