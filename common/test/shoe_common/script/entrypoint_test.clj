(ns shoe-common.script.entrypoint-test
  (:require
    [clojure.test :refer :all]
    [clojure.string :as str]
    [shoe-common.task :as task]
    [shoe-common.script.entrypoint :as enter]))

(defn my-custom-task
  {:shoe/cli-specs [["-a" "--apples N"]
                     ["-b" "--bool"]]}
  [{:keys [apples bool]}]
  (if bool
    (task/exit false)
    (task/exit true apples)))

(defn my-task2
  {:shoe/cli-specs []}
  [_]
  true)

(deftest lookup-task-cli-specs-test
  (is (enter/lookup-task-cli-specs 'shoe-tasks.repl/repl))
  (is (enter/lookup-task-cli-specs `my-custom-task)))

(deftest parse-cli-args-test
  (let [config {:refer-tasks {'task2 `my-task2
                              'fruit `my-custom-task}}]
    (is (= [{:task `my-task2 :args []}]
           (enter/parse-cli-args config ["task2"])))
    (testing "A referred task is correctly parsed"
      (is (= [{:task `my-custom-task :args ["-b"]}]
             (enter/parse-cli-args {:refer-tasks {'fruit `my-custom-task}}
                                   ["fruit" "-b"]))))
    (testing "multiple tasks"
      (is (= [{:task `my-task2 :args []}
              {:task `my-custom-task :args ["-a" "1337"]}]
             (enter/parse-cli-args config ["task2" "fruit" "-a" "1337"])))))
  (testing "Fully qualified task is correctly parsed"
    (is (= [{:task `my-custom-task :args []}]
           (enter/parse-cli-args {} [(str `my-custom-task)]))))

  (testing "Nonexistent option results in an exit map."
    (is (task/exit? (enter/parse-cli-args {} ["repl" "--teapot"])))))

(deftest built-in-task-coord-test
  (let [task-coord (fn [common-coord]
                     (-> (enter/built-in-task-coord 'shoe-task.repl/repl common-coord)
                         (get 'shoe.tasks/repl)))]
    (is (str/ends-with? (:local/root (task-coord {:local/root "."}))
                        "/tasks/repl"))
    (is (= "sha"
           (:sha (task-coord {:git/url "" :sha "sha"}))))))