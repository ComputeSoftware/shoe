(ns shoe-cli.macros
  (:require
    [clojure.java.io :as io])
  (:import (java.io FilenameFilter)))

(defmacro def-env-var
  [sym env-var-name]
  `(def ~sym ~(System/getenv env-var-name)))

(defmacro def-built-in-task-dirs
  "Defines a `sym` that contains a vector of all task directories in the /tasks
  directory."
  [sym]
  (let [task-dirs (vec (.list (io/file "../tasks")
                              (proxy [FilenameFilter] []
                                (accept [current name]
                                  (let [f (io/file current name)]
                                    (and (.isDirectory f)
                                         (.exists (io/file f "deps.edn"))))))))]
    `(def ~sym ~task-dirs)))