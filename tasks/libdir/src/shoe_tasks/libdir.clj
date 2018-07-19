(ns shoe-tasks.libdir
  (:require
    [shoe-common.fs :as fs]
    [shoe-common.log :as log]
    [shoe-common.shell :as shell]
    [shoe-common.util :as util]))

(defn copy-resources
  [cp-vec target-dir]
  (doseq [path cp-vec]
    (fs/copy path target-dir {:recursive?       true
                              :copy-attributes? true})))

(defn libdir
  {:shoe/cli-specs [["-o" "--out PATH" "Path to output the libs to."
                     :validate-fn (fn [path]
                                    (not (fs/file? path)))
                     :default "libs"]]}
  [{:keys [out]}]
  (let [cp-vec (util/parse-classpath-string (shell/classpath-string-from-clj))]
    #_(when (fs/exists? out)
        (log/info out "already exists. Overwriting.")
        (fs/delete out))
    (fs/create-directories out)
    (log/info "Copying classpath resources...")
    (copy-resources cp-vec out)))