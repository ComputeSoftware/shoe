(ns clake-cli.core
  (:require-macros clake-cli.macros)
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

(clake-cli.macros/def-edn-file core-deps "../../deps.edn")

;; def the core deps here...

;; https://nodejs.org/api/child_process.html#child_process_child_process_execsync_command_options
(defn exec-sync
  "Executes a command synchronously and sends the output to the parent process."
  ([command] (exec-sync command nil))
  ([command options]
   (child-proc/execSync command (clj->js (merge {:stdio "pipe"}
                                                options)))))

(defn exec-sync-edn
  [command]
  (edn/read-string (.toString (exec-sync command))))

(defn stringify-code
  [& code]
  (str/join " " code))

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

(defn clake-core-deps
  [])

(defn add-dev-deps
  "Adds `base-dev-deps` to your deps.edn. Will only add the dependency if it is
  not already in your deps."
  [deps-edn]
  (assoc-in deps-edn [:aliases :clake-dev] {:extra-deps '{org.clojure/tools.nrepl {:mvn/version "0.2.12"}}}))

(defn full-deps-edn
  "Returns the fully merged deps.edn as EDN."
  []
  (let [config-files (:config-files (exec-sync-edn "clj -Sdescribe"))
        deps "'{:deps {org.clojure/tools.deps.alpha {:mvn/version \"0.5.435\"}}}'"
        code (str/join " " ['(require '[clojure.tools.deps.alpha.reader :as reader])
                            (list 'reader/read-deps config-files)])
        cmd (str "clj -Sdeps " deps " -e '" code "'")]
    (exec-sync-edn cmd)))

(defn clake-jvm-classpath
  "Returns the JVM classpath for starting a Clake task."
  []
  (let [deps-edn (full-deps-edn)
        deps-edn-with-dev-deps (add-dev-deps deps-edn)]
    (str/trim-newline (.toString (exec-sync (str "clj -Sdeps '" deps-edn-with-dev-deps "' -Spath"))))))

(defn parse-cli-opts
  [args]
  (cli/parse-opts args cli-options :in-order true))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-cli-opts args)
        task-cmd (first arguments)]
    (cond
      (:help options)
      {:exit-message "Clake Help." :ok? true}
      (str/blank? task-cmd) {:exit-message "Task cannot be blank." :ok? false}
      :else {:task-cmd task-cmd})))

(defn exit
  [status msg]
  (println msg)
  (process/exit status))

(defn run-task
  [task-cmd]
  ;; start jvm with cmd string
  (let [classpath (clake-jvm-classpath)]
    (println "classpath: " classpath)
    (println (str "clj -Scp " classpath " -m clake.core " task-cmd))
    (exec-sync (str "clj -Scp " classpath " -m clake.core " task-cmd)
               {:stdio "inherit"})))

;; 1. parse opts
;; 2. load config
;; 3. start JVM with classpath
;; 4. run task -main function

(defn -main
  [& args]
  (println core-deps)
  #_(let [{:keys [ok? exit-message task-cmd]} (validate-args args)]
    (if task-cmd
      (run-task task-cmd)
      (exit (if ok? 0 1) exit-message))))