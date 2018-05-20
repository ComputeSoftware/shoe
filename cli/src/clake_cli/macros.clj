(ns clake-cli.macros)

(defmacro def-env-var
  [sym env-var-name]
  `(def ~sym ~(System/getenv env-var-name)))