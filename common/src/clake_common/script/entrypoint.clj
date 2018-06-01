(ns clake-common.script.entrypoint
  (:require
    [clojure.tools.cli :as cli]))

(defn resolve-function
  "Returns the var for `function-sym`. If `function-sym` is an unqualified symbol
  then `function-sym` retrieved from the `config`. If not in the config or
  `resolve`able then returns `nil`."
  [config function-sym]
  (if (qualified-symbol? function-sym)
    (resolve function-sym)
    (when-let [qualified-fn-sym (get-in config [:refer-tasks function-sym])]
      (resolve-function config qualified-fn-sym))))

(defn task-cli-specs
  [config task-string]
  (when-let [v (resolve-function config (symbol task-string))]
    (:clake/cli-specs (meta v))))

(defn parse-function-calls
  [config args]
  )

(defn -main
  [& args]
  (let [function-name (first args)]))