(ns shoe-common.task-test
  (:require
    [clojure.test :refer :all]
    [clojure.java.io :as io]
    [shoe-common.task :as task]
    [shoe-common.shell :as shell]
    [shoe-common.script.built-in-tasks :as built-in]))

(deftest normalize-config-test
  (testing "Built-in tasks are assoc'ed into :refer-tasks."
    (is (= {:refer-tasks built-in/default-refer-tasks}
           (task/normalize-config nil))))
  (testing "Built-in tasks don't override explicitly specified :refer-tasks"
    (is (= {:refer-tasks (assoc built-in/default-refer-tasks
                           'repl 'foo.repl/repl)}
           (task/normalize-config '{:refer-tasks {repl foo.repl/repl}}))))
  (testing "Task options are only qualified symbols."
    (is (= {:refer-tasks (merge built-in/default-refer-tasks
                                {'foo 'foo.core/foo})
            :task-opts   '{foo.core/foo {:a "a"}}}
           (task/normalize-config '{:refer-tasks {foo foo.core/foo}
                                    :task-opts   {foo {:a "a"}}})))))

(deftest load-config-test
  (testing "Able to load a config."
    (let [path (str (java.util.UUID/randomUUID) ".edn")]
      (spit path (str '{:task-opts {repl {}}}))
      (is (= {:task-opts   '{shoe-tasks.repl/repl {}}
              :refer-tasks built-in/default-refer-tasks}
             (task/load-config path)))
      (.delete (io/file path)))))

(deftest qualify-task-test
  (testing "Qualified task is returned if passed in"
    (is (= 'foo/bar (task/qualify-task {} 'foo/bar))))
  (testing "Able to qualify a built-in task"
    (is (= 'shoe-tasks.repl/repl (task/qualify-task {} 'repl))))
  (testing "Returns nil if cannot qualify the task."
    (is (nil? (task/qualify-task {} 'foo))))
  (testing "Able to qualify a task defined in the config"
    (is (= 'foo/bar (task/qualify-task {:refer-tasks '{foo foo/bar}} 'foo))))
  (testing "Able to override a built-in task with your own"
    (is (= 'foo/repl (task/qualify-task {:refer-tasks '{repl foo/repl}} 'repl)))))

(defn my-task
  {:shoe/cli-specs [["-a" "--a VAL"]]}
  [{:keys [a]}]
  (when (not= a "a")
    (task/exit false)))

(deftest execute-task-handler-test
  (with-redefs [shell/system-exit identity]
    (let [execute-successful? (fn [args]
                                (:shoe-exit/ok? (task/execute-task-handler `my-task args)))]
      (testing "Not successful when task returns an exit map"
        (is (not (execute-successful? []))))
      (testing "Invalid args is unsuccessful"
        (is (not (execute-successful? ["-a"]))))
      (testing "Task is successful if it does not return an exit map"
        (is (execute-successful? ["-a" "a"]))))))