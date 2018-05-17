(ns clake-cli.core
  (:require
    ["child_process" :as child-proc]
    ["process" :as process]
    [clojure.string :as str]
    [cljs.tools.reader.edn :as edn]
    [cljs.tools.cli :as cli]
    [clake-cli.io :as io]
    [clake-cli.macros :as macros]))

(def config-name "clake.edn")
(def jvm-entrypoint-ns 'clake-tasks.script.entrypoint)
(def clake-jvm-deps-alias :clake-jvm-deps)
(macros/def-edn-file clake-deps-edn "deps.edn")

(def cli-options
  ;; An option with a required argument
  [["-h" "--help"]
   [nil "--local-tasks" "Use local tasks dep for clake-tasks."]])

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

(defn with-clake-deps
  "Add the deps Clake needs to start tasks to the alias `clake-jvm-deps-alias`."
  [deps-edn local?]
  (assoc-in deps-edn
            [:aliases clake-jvm-deps-alias]
            {:extra-deps {'clake-tasks (if local?
                                         {:local/root "../tasks"}
                                         {:git/url   "https://github.com/ComputeSoftware/clake.git"
                                          :sha       "8630a013cd299a838da814ea06376edc771d8fe2"
                                          :deps/root "tasks"})}}))

(defn full-deps-edn
  "Returns the fully merged deps.edn as EDN."
  []
  (let [config-files (:config-files (exec-sync-edn "clj -Sdescribe"))
        deps "'{:deps {org.clojure/tools.deps.alpha {:mvn/version \"0.5.435\"}}}'"
        code (str/join " " ['(require '[clojure.tools.deps.alpha.reader :as reader])
                            (list 'reader/read-deps config-files)])
        cmd (str "clj -Sdeps " deps " -e '" code "'")]
    (exec-sync-edn cmd)))

(defn parse-cli-opts
  [args]
  (cli/parse-opts args cli-options :in-order true))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-cli-opts args)
        task-name (first arguments)]
    (cond
      (:help options)
      {:exit-message "Clake Help." :ok? true}
      (str/blank? task-name) {:exit-message "Task cannot be blank." :ok? false}
      :else {:task-name    task-name
             :task-args    (rest arguments)
             :local-tasks? (:local-tasks options)})))

(defn exit
  [status msg]
  (println msg)
  (process/exit status))

(defn run-task
  [{:keys [task-name task-args local-tasks?]}]
  (let [deps-edn (with-clake-deps (full-deps-edn) local-tasks?)
        data {:config    {}
              :task-name task-name
              :task-args task-args}
        aliases [clake-jvm-deps-alias]
        cmd (str "clj -A" (str/join aliases) " "
                 "-Sdeps '" deps-edn "' "
                 "-m " jvm-entrypoint-ns " "
                 "'" data "'")]
    ;(println cmd)
    (exec-sync cmd {:stdio "inherit"})))

(defn -main
  [& args]
  (let [{:keys [ok? exit-message task-name] :as result} (validate-args args)]
    (if task-name
      (run-task result)
      (exit (if ok? 0 1) exit-message))))