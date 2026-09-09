(ns dbnet.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [dbnet.facts :as facts]))

(defn- rules [vs] (set (map :rule vs)))

(deftest attr-set-requires-named-attributes
  (is (facts/attr-set? #{"order_id"}))
  (is (facts/attr-set? ["order_id" "line_no"]))
  (testing "an empty or absent attribute set is the input that turned the 3NF
  check off, so it must not qualify"
    (is (not (facts/attr-set? #{})))
    (is (not (facts/attr-set? nil))))
  (is (not (facts/attr-set? #{"  "})))
  (is (not (facts/attr-set? #{:order-id})))
  (is (not (facts/attr-set? "order_id"))))

(deftest usable-confidence-is-a-number-in-unit-interval
  (is (facts/usable-confidence? 0.0))
  (is (facts/usable-confidence? 1.0))
  (is (facts/usable-confidence? 0.6))
  (testing "the value that bought the advisor out of escalation"
    (is (not (facts/usable-confidence? 99.0))))
  (is (not (facts/usable-confidence? -0.1)))
  (testing "non-numeric: threw on :clj, admitted on :cljs"
    (is (not (facts/usable-confidence? "high"))))
  (is (not (facts/usable-confidence? nil)))
  (testing "NaN fails the bounds without a dedicated clause"
    (is (not (facts/usable-confidence? ##NaN)))
    (is (not (facts/usable-confidence? ##Inf)))))

(deftest well-formed-fd-checks-both-sides
  (is (facts/well-formed-fd? {:determinant #{"a"} :dependent #{"b"}}))
  (testing "the shape that THREW on the pre-change tree"
    (is (not (facts/well-formed-fd? {:determinant 5 :dependent #{"b"}}))))
  (is (not (facts/well-formed-fd? {:determinant #{"a"}})))
  (is (not (facts/well-formed-fd? nil))))

(deftest vocabulary-separates-undeclared-from-reserved
  (testing "an undeclared op is a vocabulary error"
    (is (= #{:undeclared-operation} (rules (facts/vocabulary-violations {:op :rebuild-everything})))))
  (testing "a reserved op is an authority boundary, and says why"
    (let [vs (facts/vocabulary-violations {:op :drop-production-table})]
      (is (= #{:reserved-operation} (rules vs)))
      (is (re-find #"権限外" (:detail (first vs))))))
  (testing "nil is an op nobody declared"
    (is (= #{:undeclared-operation} (rules (facts/vocabulary-violations {:op nil})))))
  (is (empty? (facts/vocabulary-violations {:op :propose-migration}))))

(deftest provenance-is-a-fact-about-the-record-not-the-lookup
  (testing "the empty client map registers under the key nil; asking only
  whether the store returned something admitted it"
    (is (= #{:unidentified-client}
           (rules (facts/provenance-violations {} {})))))
  (is (= #{:no-client} (rules (facts/provenance-violations {:client-id "c1"} nil))))
  (is (= #{:client-mismatch}
         (rules (facts/provenance-violations {:client-id "c1"} {:client-id "c2"}))))
  (is (empty? (facts/provenance-violations {:client-id "c1"} {:client-id "c1"}))))

(deftest schema-binding-applies-to-every-binding-op
  (testing "including :apply-migration, which used to be exempt"
    (is (= #{:no-fds-declared}
           (rules (facts/schema-binding-violations
                   {:op :apply-migration :schema-id "orders"})))))
  (is (= #{:no-fds-declared}
         (rules (facts/schema-binding-violations
                 {:op :propose-migration :schema-id "orders" :fds []}))))
  (testing "absent and empty are the same claim; a present unreadable value is not"
    (is (= #{:unreadable-fds}
           (rules (facts/schema-binding-violations
                   {:op :propose-migration :schema-id "orders" :fds "many"})))))
  (is (= #{:no-schema-cited}
         (rules (facts/schema-binding-violations
                 {:op :propose-migration :fds [{:determinant #{"a"} :dependent #{"b"}}]}))))
  (is (= #{:malformed-fd}
         (rules (facts/schema-binding-violations
                 {:op :propose-migration :schema-id "orders"
                  :fds [{:determinant 5 :dependent #{"b"}}]}))))
  (testing "non-binding operations are exempt by declaration, not by omission"
    (is (empty? (facts/schema-binding-violations {:op :flag-anomaly}))))
  (is (empty? (facts/schema-binding-violations
               {:op :propose-migration :schema-id "orders"
                :fds [{:determinant #{"a"} :dependent #{"b"}}]}))))

(deftest a-key-with-no-attributes-is-reported-not-tolerated
  (testing "clojure.set/subset? of nil is true for every set, so one such key
  makes every determinant a superkey and admits every transitive dependency"
    (let [vs (facts/key-record-violations :propose-migration
                                          [{:key-id "K-BAD" :schema-id "legacy"}])]
      (is (= #{:unusable-registered-key} (rules vs)))
      (is (re-find #"K-BAD" (:detail (first vs))))))
  (is (empty? (facts/key-record-violations
               :propose-migration [{:key-id "K-1" :attrs #{"order_id"}}])))
  (testing "not consulted for non-binding operations"
    (is (empty? (facts/key-record-violations :flag-anomaly [{:key-id "K-BAD"}])))))

(deftest confidence-violations-leave-absence-to-the-governor
  (testing "absent already reads as 0.0 there and escalates — the safe direction"
    (is (empty? (facts/confidence-violations {}))))
  (is (empty? (facts/confidence-violations {:confidence 0.9})))
  (is (= #{:unusable-confidence} (rules (facts/confidence-violations {:confidence 99.0}))))
  (is (= #{:unusable-confidence} (rules (facts/confidence-violations {:confidence "high"})))))
