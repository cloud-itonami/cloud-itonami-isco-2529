(ns dbnet.operation-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [dbnet.operation :as op]))

(deftest supported-and-reserved-are-disjoint
  (testing "an op in both maps would make the refusal reason depend on lookup order"
    (is (empty? (set/intersection (set (keys op/supported))
                                          (set (keys op/reserved)))))))

(deftest every-supported-op-declares-its-properties
  (testing "a missing :escalates? reads as false, which would silently drop a
  human sign-off; a missing :schema-op? reads as false, which would silently
  exempt the op from the 3NF check. Both are the exact failure this namespace
  was created to remove, so absence is not allowed to mean false."
    (doseq [[o m] op/supported]
      (is (contains? m :escalates?) (str o " must declare :escalates?"))
      (is (contains? m :schema-op?) (str o " must declare :schema-op?"))
      (is (string? (:summary m)) (str o " must declare a :summary")))))

(deftest every-reserved-op-says-why
  (doseq [[o m] op/reserved]
    (is (string? (:reason m)) (str o " must declare a :reason"))
    (is (seq (:reason m)))))

(deftest undeclared-ops-are-not-supported
  (is (not (op/supported? :rebuild-everything)))
  (is (not (op/declared? :rebuild-everything)))
  (is (not (op/supported? nil)))
  (is (not (op/declared? nil))))

(deftest reserved-ops-are-declared-but-never-supported
  (testing "declared so the refusal can say why; never supported so it refuses"
    (doseq [o (keys op/reserved)]
      (is (op/declared? o))
      (is (not (op/supported? o)))
      (is (string? (op/reserved-reason o))))))

(deftest apply-migration-binds-to-a-schema
  (testing "this is the field whose absence exempted the production migration
  from every schema check"
    (is (op/schema-op? :apply-migration))
    (is (op/escalates? :apply-migration))))

(deftest propose-migration-binds-but-does-not-escalate
  (is (op/schema-op? :propose-migration))
  (is (not (op/escalates? :propose-migration))))

(deftest non-binding-ops-do-not-claim-a-schema
  (is (not (op/schema-op? :draft-schema-review)))
  (is (not (op/schema-op? :flag-anomaly))))

(deftest predicates-are-false-for-undeclared-ops
  (testing "the governor hard-blocks first, so a false here is never an admission"
    (is (not (op/escalates? :rebuild-everything)))
    (is (not (op/schema-op? :rebuild-everything)))
    (is (nil? (op/reserved-reason :rebuild-everything)))))
