(ns clake-cli.clj-entrypoint
  (:require
    [clojure.tools.cli :as cli]
    [clake-tasks.built-in :as built-in]))

(defn validate-args
  [args]
  (let []))

(def built-in-tasks
  )

(defn load-task
  [task-name])

(defn -main
  [& args]
  (let [task-name (first args)]))