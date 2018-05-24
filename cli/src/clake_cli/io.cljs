(ns clake-cli.io
  (:refer-clojure :exclude [exists? resolve])
  (:require
    ["fs" :as fs]
    ["path" :as path]
    [cljs.tools.reader.edn :as edn]))

(defn exists?
  [path]
  (fs/existsSync path))

(defn file?
  [path]
  (.isFile (fs/lstatSync path)))

(defn directory?
  [path]
  (.isDirectory (fs/lstatSync path)))

(defn resolve
  [p]
  (path/resolve p))

(defn slurp
  ([filename] (slurp filename nil))
  ([filename {:keys [encoding]}]
   (.toString (fs/readFileSync filename encoding))))

(defn slurp-edn
  [filename]
  (edn/read-string (slurp filename)))

(defn spit
  ([filename data] (spit filename data nil))
  ([filename data {:keys [encoding mode flag]
                   :or   {encoding "utf8"
                          mode     "0o666"
                          flag     "w"}}]
   (fs/writeFileSync filename data encoding mode flag)))

(defn delete
  [path]
  (fs/unlinkSync path))

(defn buffer-from-string
  [str]
  (Buffer/from str "utf8"))