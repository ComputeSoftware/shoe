(ns clake-tasks.repl
  (:require
    [clojure.java.io :as io]
    [clojure.tools.nrepl.server :as nrepl-server]
    [clake-common.log :as log]
    [clake-common.task :as task]
    [clake-common.script.built-in-tasks :as tasks])
  (:import (java.nio.file Files)))

(defn repl
  {:clake/cli-specs   (tasks/cli-spec `repl)
   :clake/shutdown-fn (fn []
                        (Files/deleteIfExists (.toPath (io/file ".nrepl-port"))))}
  [{:keys [port lein-port]
    :or   {port 0}
    :as   task-opts}]
  (let [#_#_repl (main/repl)
        server (nrepl-server/start-server :port port)
        selected-port (:port server)]
    (log/info (format "nREPL server started on port %s" selected-port))
    (when true #_lein-port
      (spit ".nrepl-port" selected-port))
    @(promise)))

(task/def-task-main repl)