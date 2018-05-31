(ns clake-common.specs2
  (:require
    [clojure.spec.alpha :as s]))

(comment
  {:port {:parse-fn #(Integer/parseInt %)
          :default  0
          :spec     (s/int-in 0 100)
          :short    "-p"
          :doc      "Port to start nREPL server on."}}
  )

(s/def ::task-option (s/keys))