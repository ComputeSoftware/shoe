(ns clake.built-in
  (:require
    [clojure.main :as main]
    [clojure.tools.nrepl.server :as nrepl-server]))

(defn nrepl
  []
  (let [repl (main/repl)
        server (nrepl-server/start-server)]
    (println "nREPL server started on port " (:port server))))