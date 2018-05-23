(ns clake-tasks.log
  (:require
    [clojure.string :as str]))

(defn log*
  [stream strings]
  (binding [*out* stream]
    (println (str/join " " strings))))

(defn prefix-strings
  "Adds `prefix` to the start of the `strings` collection if the first string
  in `strings` does not start with `prefix`."
  [prefix strings]
  (if (str/starts-with? (or (first strings) "") prefix)
    strings
    (vec (concat [prefix] strings))))

(defn info
  [& strings]
  (log* #?(:clj *out* :cljs (.-stdout js/process))
        strings))

(defn warn
  [& strings]
  (apply info (prefix-strings "warning:" strings)))

(defn error
  [& strings]
  (log* #?(:clj *err* :cljs (.-stderr js/process))
        (prefix-strings "error:" strings)))