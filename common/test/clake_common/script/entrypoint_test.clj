(ns clake-common.script.entrypoint-test
  (:require
    [clojure.test :refer :all]
    [clake-common.shell :as shell]
    [clake-common.script.entrypoint :as enter]
    [clojure.string :as str]))

(deftest qualify-task-test
  (testing "Qualified task is returned if passed in"
    (is (= 'foo/bar (enter/qualify-task {} 'foo/bar))))
  (testing "Able to qualify a built-in task"
    (is (= 'clake-tasks.repl/repl (enter/qualify-task {} 'repl))))
  (testing "Returns nil if cannot qualify the task."
    (is (nil? (enter/qualify-task {} 'foo))))
  (testing "Able to qualify a task defined in the config"
    (is (= 'foo/bar (enter/qualify-task {:refer-tasks '{foo foo/bar}} 'foo))))
  (testing "Able to override a built-in task with your own"
    (is (= 'foo/repl (enter/qualify-task {:refer-tasks '{repl foo/repl}} 'repl)))))

(defn my-custom-task
  {:clake/cli-specs [["-a" "--apples N"]
                     ["-b" "--bool"]]}
  [{:keys [apples bool]}]
  (if bool
    (shell/exit false)
    (shell/exit true apples)))

(deftest lookup-task-cli-specs-test
  (is (enter/lookup-task-cli-specs 'clake-tasks.repl/repl))
  (is (enter/lookup-task-cli-specs `my-custom-task)))

(deftest parse-cli-args-test
  (is (= [{:task 'clake-tasks.repl/repl :args []}]
         (enter/parse-cli-args {} ["repl"])))
  (is (= [{:task 'clake-tasks.repl/repl :args ["-p" "8888"]}]
         (enter/parse-cli-args {} ["repl" "-p" "8888"])))
  (is (= [{:task 'clake-tasks.test/test :args []}
          {:task 'clake-tasks.repl/repl :args ["-p" "8888"]}]
         (enter/parse-cli-args {} ["test" "repl" "-p" "8888"])))
  (testing "Fully qualified task is correctly parsed"
    (is (= [{:task `my-custom-task :args []}]
           (enter/parse-cli-args {} [(str `my-custom-task)]))))
  (testing "A referred task is correctly parsed"
    (is (= [{:task `my-custom-task :args ["-b"]}]
           (enter/parse-cli-args {:refer-tasks {'fruit `my-custom-task}}
                                 ["fruit" "-b"]))))
  (testing "Nonexistent option results in an exit map."
    (is (shell/exit? (enter/parse-cli-args {} ["repl" "--teapot"])))))

(deftest task-clojure-command-test
  (let [task-deps (fn [deps]
                    (-> (enter/task-clojure-command
                          'clake-task.repl/repl ["test"] deps [])
                        (get-in [:deps-edn :deps 'clake-tasks.repl])))]
    (testing ":local/root relative directory"
      (is (str/ends-with? (:local/root (task-deps {'clake-common {:local/root "../../common"}}))
                          "/tasks/repl")))
    (testing "git coord"
      (is (= "sha"
             (:sha (task-deps {'clake-common {:git/url ""
                                              :sha     "sha"}})))))))