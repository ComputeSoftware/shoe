(ns dev.ci
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.edn :as edn])
  (:import (java.io FilenameFilter)))

;; the reason for this nightmare is to keep the formatting of a deps.edn exactly
;; the same and only replace the `:sha` for clake-common in a deps.edn.
(defn replace-clake-common-sha
  [deps-string new-sha]
  (let [clake-name "clake-common"
        name-index (str/index-of deps-string clake-name)
        map-start-i (reduce (fn [i char]
                              (if (= char \{)
                                (reduced (inc i))
                                (inc i)))
                            name-index (drop name-index deps-string))
        sha-i (+ map-start-i
                 (str/index-of (apply str (drop map-start-i deps-string)) ":sha")
                 ;; + 4 for length of :sha
                 4)
        find-quote-idx (fn [s]
                         (reduce (fn [i char]
                                   (if (= char \")
                                     (reduced (inc i))
                                     (inc i)))
                                 0 s))
        sha-start-i (+ sha-i (find-quote-idx (drop sha-i deps-string)))
        sha-end-i (+ sha-start-i
                     (find-quote-idx (drop sha-start-i deps-string))
                     ;; subtract 1 to add the " back in
                     -1)]
    (str (subs deps-string 0 sha-start-i)
         new-sha
         (subs deps-string sha-end-i (count deps-string)))))

(defn clake-common-git-dep?
  [deps-string]
  (some? (get-in (edn/read-string deps-string) [:deps 'clake-common :sha])))

(defn task-deps-edn-paths
  [root-path]
  (let [tasks-parent (io/file root-path "tasks")]
    (mapv
      (fn [task-root]
        (.getAbsolutePath (io/file tasks-parent task-root "deps.edn")))
      (.list tasks-parent
             (proxy [FilenameFilter] []
               (accept [current name]
                 (and (.isDirectory (io/file current name))
                      (not (str/starts-with? name "."))
                      (.exists (io/file current name "deps.edn")))))))))

(defn replace-git-sha
  {:clake/cli-specs [["-s" "--sha VAL" "The SHA to replace in all the tasks."]]}
  [{:keys [sha]}]
  (assert sha ":sha not set.")
  (doseq [p (task-deps-edn-paths "..")]
    (let [deps-str (slurp p)]
      (if (clake-common-git-dep? deps-str)
        (spit p (replace-clake-common-sha deps-str sha))))))

(comment
  (def deps-edn-str
    "{:deps    {org.clojure/clojure     {:mvn/version \"1.9.0\"}
           org.clojure/spec.alpha  {:mvn/version \"0.1.143\"}
           org.clojure/tools.nrepl {:mvn/version \"0.2.13\"}
           clake-common            {:git/url   \"https://github.com/ComputeSoftware/clake\"
                                    :deps/root \"common\"
                                    :sha       \"3e1c1ede14f47674188f2170265b44c5eb1eaaeb\"}}
 ;; override-deps does not work correctly right now so we need to use the comment approach
 ;; https://dev.clojure.org/jira/browse/TDEPS-51
 :aliases {:dev  {:override-deps {clake-common {:local/root \"../../common\"}}}
           :repl {:main-opts [\"-m\" \"clake-tasks.repl\"]}}}")

  (def deps-edn-str
    "{:deps {org.clojure/clojure         {:mvn/version \"1.9.0\"}
 pjstadig/humane-test-output {:mvn/version \"0.8.3\"}
 com.cognitect/test-runner   {:git/url \"https://github.com/cognitect-labs/test-runner.git\"
                              :sha     \"78d380d00e7a27f7b835bb90af37e73b20c49bcc\"}
 clake-common                {:local/root \"../../common\"}}}")
  )