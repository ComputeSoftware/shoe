(ns clake-tasks.repl
  (:require
    [clojure.java.io :as io]
    [clojure.main :as main]
    [clojure.tools.nrepl.server :as nrepl-server]
    [clake-common.log :as log]
    [clake-common.task :as task])
  (:import (java.nio.file Files)))

(defn repl
  {:clake/cli-specs   [["-p" "--port PORT" "Port to start nREPL server on."
                        :parse-fn #(Integer/parseInt %)]
                       ["-s" "--server-only" "Only start a nREPL server."]]
   :clake/shutdown-fn (fn []
                        (Files/deleteIfExists (.toPath (io/file ".nrepl-port"))))}
  [{:keys [port server-only]
    :or   {port 0}
    :as   task-opts}]
  (let [server (nrepl-server/start-server :port port)
        selected-port (:port server)]
    (log/info (format "nREPL server started on port %s" selected-port))
    (when true #_lein-port
      (spit ".nrepl-port" selected-port))
    (when-not server-only
      (main/repl))
    @(promise)))

(task/def-task-main repl)