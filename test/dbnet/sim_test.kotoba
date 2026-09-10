(ns dbnet.sim-test
  (:require [clojure.test :refer [deftest is testing]]
            [dbnet.sim :as sim]))

(deftest the-scenario-run-passes
  (let [r (sim/run)]
    (is (:ok? r) (sim/report r))
    (is (empty? (:mismatches r)))
    (is (empty? (:wrote-anyway r)))
    (is (empty? (:ledger-breaks r)))))

(deftest the-run-demonstrates-refusals
  (testing "a governed actor's claim is that there exist actions it refuses.
  A table that stopped exercising the governor would otherwise print green
  while demonstrating nothing."
    (is (pos? (:refusals (sim/run))))))

(deftest a-refusal-never-writes
  (let [r (sim/run)]
    (doseq [s (:results r) :when (:refusal? s)]
      (is (not (:wrote? s)) (str (:name s) " refused but wrote a record")))))

(deftest every-ledger-verifies
  (doseq [s (:results (sim/run))]
    (is (:ok? (:ledger-verify s)) (str (:name s) " left a broken ledger"))))

(deftest report-refuses-to-pass-a-run-with-no-refusals
  (testing "the harness's own failure mode: zero refusals must not read as a pass"
    (let [txt (sim/report {:results [] :refusals 0 :mismatches []
                           :wrote-anyway [] :ledger-breaks [] :ok? false})]
      (is (re-find #"REFUSING TO REPORT A PASS" txt)))))

(deftest scenario-names-are-unique
  (testing "duplicated names would let one scenario silently shadow another in
  the report and in checkpoint thread ids"
    (let [ns- (map :name sim/scenarios)]
      (is (= (count ns-) (count (set ns-)))))))
