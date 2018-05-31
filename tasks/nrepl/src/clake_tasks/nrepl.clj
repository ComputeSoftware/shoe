(ns clake-tasks.nrepl
  (:require
    [clojure.java.io :as io]
    [clojure.tools.nrepl.server :as nrepl-server]
    [clake-common.log :as log])
  (:import (java.nio.file Files)))

(defn nrepl
  [{:keys [port lein-port] :as task-opts}]
  (let [#_#_repl (main/repl)
        server (nrepl-server/start-server :port 0)
        selected-port (:port server)]
    (log/info (format "nREPL server started on port %s" selected-port))
    (when lein-port
      (spit ".nrepl-port" selected-port))
    @(promise)))