(ns clake.core
  #_(:require
    [clojure.tools.cli :as cli]))

;; should tasks be named with keywords?
;; - load task namespaces
;; - execute task based on name
;; - able to execute multiple tasks serially
(defn -main
  [& args]
  (println "hello world")
  (prn args))
