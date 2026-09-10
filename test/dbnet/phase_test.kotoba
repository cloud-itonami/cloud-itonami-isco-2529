(ns dbnet.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [dbnet.phase :as phase]))

(deftest hard-beats-escalate
  (testing "a verdict that is BOTH hard-blocked and escalating must hold.
  Escalating it would ask a human to approve something no human may approve —
  and for this actor the two coincide on :apply-migration, the request most
  likely to be waved through."
    (is (= :hold (phase/of-verdict {:hard? true :escalate? true})))))

(deftest routes-each-verdict
  (is (= :hold (phase/of-verdict {:hard? true})))
  (is (= :request-approval (phase/of-verdict {:hard? false :escalate? true})))
  (is (= :commit (phase/of-verdict {:hard? false :escalate? false}))))

(deftest only-commit-writes
  (is (phase/writes? :commit))
  (is (not (phase/writes? :hold)))
  (testing "an escalation has NOT written yet — it is a refusal to act without a human"
    (is (not (phase/writes? :request-approval)))))

(deftest both-non-writing-phases-are-refusals
  (is (phase/refusal? :hold))
  (is (phase/refusal? :request-approval))
  (is (not (phase/refusal? :commit))))

(deftest only-escalation-requires-a-human
  (is (phase/human-required? :request-approval))
  (is (not (phase/human-required? :commit)))
  (is (not (phase/human-required? :hold))))

(deftest escalated-commits-are-attributable-to-a-human
  (testing "the commit node uses this to distinguish a human-approved
  production migration from an automatic one; pre-change the ledger could not"
    (is (phase/approved-commit? :request-approval))
    (is (not (phase/approved-commit? :commit)))))

(deftest unknown-phase-never-claims-a-write
  (testing "a typo'd phase must not read as writable"
    (is (not (phase/writes? :nonsense)))
    (is (not (phase/human-required? :nonsense)))))
