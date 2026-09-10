(ns dbnet.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [dbnet.actor :as actor]
            [dbnet.ledger :as ledger]
            [dbnet.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-key! st {:key-id "K-1" :client-id "client-1"
                             :schema-id "orders" :attrs #{"order-id"}})
    st))

(deftest commits-a-3nf-migration
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :propose-migration :stake :low
                 :schema-id "orders"
                 :fds [{:determinant #{"order-id"} :dependent #{"customer-id"}}]}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-transitive-dependency-migration
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :propose-migration :stake :low
                 :schema-id "orders"
                 :fds [{:determinant #{"customer-id"} :dependent #{"customer-name"}}]}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-applies-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; :fds are required for a schema-binding operation now; without
        ;; them this request holds rather than interrupting, which is the
        ;; point of the change and is asserted in governor-test.
        request {:client-id "client-1" :op :apply-migration :stake :high
                 :schema-id "orders"
                 :fds [{:determinant #{"order-id"} :dependent #{"customer-id"}}]}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))

(deftest the-ledger-the-graph-leaves-behind-is-chained-across-runs
  (testing "every dbnet.sim scenario leaves a single-entry ledger, so the
  chaining property — each entry committing to its predecessor — is not
  exercised end-to-end there. Running several requests against ONE store is
  what makes the chain longer than one link and therefore checkable through
  the wired graph rather than only over `ledger/append`."
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          good {:client-id "client-1" :op :propose-migration :stake :low
                :schema-id "orders"
                :fds [{:determinant #{"order-id"} :dependent #{"customer-id"}}]}
          bad (assoc good :fds [{:determinant #{"customer-id"}
                                 :dependent #{"customer-name"}}])]
      (actor/run-request! graph good {} "chain-1")
      (actor/run-request! graph bad {} "chain-2")
      (actor/run-request! graph good {} "chain-3")
      (let [l (vec (store/ledger st))]
        (is (= 3 (count l)))
        (is (= [0 1 2] (mapv :ledger/seq l)))
        (is (= [:commit :hold :commit] (mapv :disposition l)))
        (is (:ok? (ledger/verify l)))
        (testing "and the chain detects a tampered interior entry"
          (is (not (:ok? (ledger/verify (assoc-in l [1 :disposition] :commit))))))))))

(deftest an-automatic-commit-is-distinguishable-from-a-human-approved-one
  (testing "the reason the interrupt exists: pre-change both left
  {:disposition :commit} with no field telling them apart"
    (let [st (fresh-store)
          graph (actor/build-graph {:store st})
          auto {:client-id "client-1" :op :propose-migration :stake :low
                :schema-id "orders"
                :fds [{:determinant #{"order-id"} :dependent #{"customer-id"}}]}
          escalating (assoc auto :op :apply-migration)]
      (actor/run-request! graph auto {} "who-1")
      (actor/run-request! graph escalating {} "who-2")
      (actor/approve! graph "who-2")
      (is (= [:actor :human] (mapv :approved-by (store/ledger st)))))))
