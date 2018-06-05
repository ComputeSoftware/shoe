(ns clake-common.script.built-in-tasks
  (:require
    [clojure.string :as str]
    [clojure.edn :as edn]))

;; TODO: Ideally this would be auto-generated
(def default-refer-tasks
  '{repl    clake-tasks.repl/repl
    test    clake-tasks.test/test
    uberjar clake-tasks.uberjar/uberjar})

(defn- parse-kw
  [s]
  (if (.startsWith s ":") (read-string s) (keyword s)))

(defn- accumulate
  [m k v]
  (update-in m [k] (fnil conj #{}) v))

(def cli-specs
  {'clake-tasks.repl/repl [["-p" "--port PORT" "Port to start nREPL server on."
                            :parse-fn #(Integer/parseInt %)]
                           ["-l" "--lein-port" "Spit the port to .nrepl-port."]]
   'clake-tasks.test/test [["-d" "--dir DIRNAME" "Name of the directory containing tests. Defaults to \"test\"."
                            :parse-fn str
                            :assoc-fn accumulate]
                           ["-n" "--namespace SYMBOL" "Symbol indicating a specific namespace to test."
                            :parse-fn symbol
                            :assoc-fn accumulate]
                           ["-r" "--namespace-regex REGEX" "Regex for namespaces to test. Defaults to #\".*-test$\"\n(i.e, only namespaces ending in '-test' are evaluated)"
                            :parse-fn re-pattern
                            :assoc-fn accumulate]
                           ["-v" "--var SYMBOL" "Symbol indicating the fully qualified name of a specific test."
                            :parse-fn symbol
                            :assoc-fn accumulate]
                           ["-i" "--include KEYWORD" "Run only tests that have this metadata keyword."
                            :parse-fn parse-kw
                            :assoc-fn accumulate]
                           ["-e" "--exclude KEYWORD" "Exclude tests with this metadata keyword."
                            :parse-fn parse-kw
                            :assoc-fn accumulate]]})

(defn cli-spec
  "Returns the CLI spec for `qualified-task` from the built in CLI specs."
  [qualified-task]
  (get cli-specs qualified-task []))

(defn built-in?
  "Returns true if `qualified-task` is a built in task."
  [qualified-task]
  (some? (cli-spec qualified-task)))