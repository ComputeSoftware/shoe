(ns clake-cli.core
  (:require
    ["child_process" :as child-proc]
    ["process" :as process]
    [clojure.string :as str]
    [cljs.tools.reader.edn :as edn]
    [cljs.tools.cli :as cli]
    [clake-cli.io :as io]))

(def config-name "clake.edn")
(def cli-options
  ;; An option with a required argument
  [["-h" "--help"]])

;; https://nodejs.org/api/child_process.html#child_process_child_process_execsync_command_options
(defn exec-sync
  "Executes a command synchronously and sends the output to the parent process."
  [command]
  (child-proc/execSync command #{:stdio "inherit"}))

(defn load-config
  [config-path]
  (when (io/exists? config-path)
    (io/slurp-edn config-path)))

;; we'll eventually need to start a jvm to get classpath and to launch the REPL
;; https://github.com/clojure/brew-install/blob/1.9.0/src/main/resources/clojure
;; clojure.main options: https://clojure.org/reference/repl_and_main
;; also need to launch an nrepl. may need a config option for that
(defn start-jvm
  []
  )

;; call tools-deps to get the jvm classpath
(defn tools-deps-jvm-classpath
  [])

(def repl-deps
  '[[org.clojure/tools.nrepl "0.2.12"]])

(defn start-repl
  []
  (let [start-repl '((require '[clojure.tools.nrepl.server])
                      (defonce server (clojure.tools.nrepl.server/start-server :port 7889))
                      (println (str "nREPL server started on port " (:port server) " on host 127.0.0.1")))
        cmd (str "clj -e \"" (str/join " " start-repl) "\" -r")]
    (exec-sync cmd)))

(defn parse-cli-opts
  [args]
  (cli/parse-opts args cli-options :in-order true))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-cli-opts args)]
    (cond
      (:help options)
      {:exit-message "" :ok? true})))

(defn exit
  [status msg]
  (println msg)
  (process/exit status))

;; 1. parse opts
;; 2. load config
;; 3. start JVM with classpath
;; 4. run task -main function

(defn -main
  [& args]
  (validate-args args))