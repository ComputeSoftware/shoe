(ns clake-tasks.api-test
  (:require
    [clojure.test :refer :all]
    [clake-tasks.api :as api]))

(deftest test-exit
  (is (= {:clake-exit/status 0
          :clake-exit/ok?    true}
         (api/exit true)))
  (is (= {:clake-exit/status 1
          :clake-exit/ok?    false}
         (api/exit false)))
  (is (= {:clake-exit/status 10
          :clake-exit/ok?    false}
         (api/exit 10)))
  (is (= {:clake-exit/status  0
          :clake-exit/ok?     true
          :clake-exit/message "msg"}
         (api/exit true "msg"))))

(deftest test-exit?
  (is (api/exit? (api/exit true)))
  (is (api/exit? (api/exit false)))
  (is (api/exit? (api/exit 1))))