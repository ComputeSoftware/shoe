(ns clake-cli.macros
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]))

(defmacro def-edn-file
  [sym filename]
  `(def ~sym ~(edn/read-string (slurp (io/file filename)))))