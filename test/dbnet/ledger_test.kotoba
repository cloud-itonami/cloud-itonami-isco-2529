(ns dbnet.ledger-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [dbnet.ledger :as led]))

(defn- three []
  (-> []
      (led/append {:disposition :commit :record {:op :propose-migration}})
      (led/append {:disposition :hold :verdict {:hard? true}})
      (led/append {:disposition :commit :record {:op :apply-migration}})))

(deftest append-chains-position-and-content
  (let [l (three)]
    (is (= [0 1 2] (mapv :ledger/seq l)))
    (is (= 0 (:ledger/prev (first l))))
    (is (= (:ledger/hash (first l)) (:ledger/prev (second l))))
    (is (:ok? (led/verify l)))
    (is (= 3 (:length (led/verify l))))))

(deftest an-empty-ledger-verifies
  (is (:ok? (led/verify []))))

(deftest content-tampering-is-detected
  (let [l (three)
        tampered (assoc-in l [1 :disposition] :commit)]
    (testing "flipping a refusal into a write is the tamper that matters most"
      (is (not (:ok? (led/verify tampered))))
      (is (= 1 (:broken-at (led/verify tampered))))
      (is (= :hash-mismatch (:reason (led/verify tampered)))))))

(deftest reordering-is-detected
  (let [l (three)
        swapped (assoc l 0 (nth l 1) 1 (nth l 0))]
    (is (not (:ok? (led/verify swapped))))))

(deftest dropping-an-interior-entry-is-detected
  (let [l (three)
        dropped (vec (concat [(nth l 0)] [(nth l 2)]))]
    (is (not (:ok? (led/verify dropped))))
    (is (= :seq-mismatch (:reason (led/verify dropped))))))

(deftest truncation-at-the-tail-verifies-and-that-limit-is-stated
  (testing "a chain cannot detect entries it never saw; verify claims only
  what it can show rather than returning a bare false"
    (is (:ok? (led/verify (vec (butlast (three))))))))

(deftest hash-is-deterministic
  (is (= (led/chain-hash 0 {:a 1}) (led/chain-hash 0 {:a 1})))
  (is (not= (led/chain-hash 0 {:a 1}) (led/chain-hash 0 {:a 2})))
  (testing "position is committed to, so the same content at a different
  position hashes differently"
    (is (not= (led/chain-hash 0 {:a 1}) (led/chain-hash 7 {:a 1}))))
  (testing "stays inside the exactly-representable integer range on both hosts"
    (is (< (led/chain-hash 12345 {:a "x"}) 2147483647))
    (is (>= (led/chain-hash 12345 {:a "x"}) 0))))

(deftest commit-entries-record-who-approved-them
  (testing "pre-change a human-approved production migration and an automatic
  commit left two indistinguishable {:disposition :commit} entries"
    (is (= :human (:approved-by (led/commit-entry {:op :apply-migration} :human))))
    (is (= :actor (:approved-by (led/commit-entry {:op :propose-migration} :actor))))
    (testing ":actor is recorded explicitly so an absent field cannot be
    mistaken for an unaudited one"
      (is (contains? (led/commit-entry {} :actor) :approved-by)))))

(deftest hold-entries-carry-the-reason
  (let [e (led/hold-entry {:hard? true :violations [{:rule :transitive-dependency}]})]
    (is (= :hold (:disposition e)))
    (is (= :none (:approved-by e)))
    (is (= [{:rule :transitive-dependency}] (:violations (:verdict e))))))

(deftest summary-names-every-entry
  (let [s (led/summary (three))]
    (is (re-find #"approved-by=unrecorded" s))
    (is (= 3 (count (str/split-lines s))))))
