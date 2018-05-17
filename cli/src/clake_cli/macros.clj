(ns clake-cli.macros
  (:require
    [clojure.edn :as edn]))

(defmacro def-edn-file
  [sym filename]
  `(def ~sym '~(edn/read-string (slurp filename))))