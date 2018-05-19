(ns clake-cli.core
  (:require
    ["child_process" :as child-proc]
    ["process" :as process]
    [clojure.string :as str]
    [clojure.set :as sets]
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
   [nil "--local-tasks" "Use local tasks dep for clake-tasks."]
   [nil "--tasks-sha SHA" "The git SHA to use for the clake-tasks dependency."]])

(defn usage-text
  [options-summary]
  (->> ["Usage: clake [clake-opt*] [task] [arg*]"
        ""
        "Clake is a build tool for Clojure(Script). It uses the Clojure CLI to "
        "start a JVM."
        ""
        "Options:"
        options-summary
        ""]
       ;; it'd be nice to show a list of available tasks but that is only possible
       ;; if we start a JVM.
       (str/join "\n")))

(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

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
  [deps-edn {:keys [local-tasks tasks-sha]}]
  (assoc-in deps-edn
            [:aliases clake-jvm-deps-alias]
            {:extra-deps {'clake-tasks (if local-tasks
                                         {:local/root "../tasks"}
                                         {:git/url   "git@github.com:ComputeSoftware/clake.git"
                                          :sha       (or tasks-sha "da722e5bdc8c679b07e05a256717c6c57a5ed4f3")
                                          :deps/root "tasks"})}}))

(defn full-deps-edn
  "Returns the fully merged deps.edn as EDN."
  []
  (let [config-files (:config-files (exec-sync-edn "clojure -Sdescribe"))
        deps "'{:deps {org.clojure/tools.deps.alpha {:mvn/version \"0.5.435\"}}}'"
        code (str/join " " ['(require '[clojure.tools.deps.alpha.reader :as reader])
                            (list 'reader/read-deps config-files)])
        cmd (str "clojure -Sdeps " deps " -e '" code "'")]
    (exec-sync-edn cmd)))

(defn aliases-from-config
  "Returns a set of all aliases set in the `:task-opts` key in the config for the
  given `task-cli-args`."
  [task-cli-args config]
  (let [tasks-in-args (into #{}
                            (map symbol)
                            (:arguments (cli/parse-opts task-cli-args [])))]
    (reduce-kv (fn [all-aliases task-name {:keys [aliases]}]
                 (if (contains? tasks-in-args task-name)
                   (sets/union all-aliases (set aliases))
                   all-aliases)) #{} (:task-opts config))))

(defn parse-cli-opts
  [args]
  (cli/parse-opts args cli-options :in-order true))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-cli-opts args)]
    (cond
      (:help options)
      {:exit-message (usage-text summary) :ok? true}
      errors
      {:exit-message (error-msg errors) :ok? false}
      (nil? (first arguments))
      {:exit-message "Error: Missing task name." :ok? false}
      :else {:clake/task-cli-args arguments
             :clake/cli-opts      options})))

(defn exit
  [status msg]
  (println msg)
  (process/exit status))

(defn run-task
  [context]
  (let [deps-edn (with-clake-deps (full-deps-edn) (:clake/cli-opts context))
        config (load-config config-name)
        context (assoc context :clake/config config
                               :clake/deps-edn deps-edn)
        aliases (conj (aliases-from-config (:clake/task-cli-args context) config)
                      clake-jvm-deps-alias)
        cmd (str "clojure -A" (str/join aliases) " "
                 "-Sdeps '" deps-edn "' "
                 "-m " jvm-entrypoint-ns " "
                 "'" context "'")]
    ;(println cmd)
    (exec-sync cmd {:stdio "inherit"})))

(defn clj-installed?
  []
  (try
    (some? (exec-sync "clojure --help"))
    (catch js/Error _ false)))

(defn -main
  [& args]
  (if (clj-installed?)
    (let [{:keys [ok? exit-message] :as context} (validate-args args)]
      (if exit-message
        (exit (if ok? 0 1) exit-message)
        (run-task context)))
    (exit 1 (str "Clojure CLI tools are not installed or globally accessible.\n"
                 "Install guide:\n"
                 "  https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools"))))