(ns shoe-common.cli-utils
  (:require
    [clojure.string :as str]
    [clojure.tools.cli :as cli]))

(defn select-cli-specs
  [cli-specs ids]
  (filter
    some?
    (map (fn [cli-spec {:keys [id]}]
           (when (contains? ids id)
             cli-spec))
         cli-specs (#'cli/compile-option-specs cli-specs))))

;; Parsers

(defn commas-list
  [s]
  (str/split s #","))