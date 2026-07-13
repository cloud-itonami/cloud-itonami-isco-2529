(ns dbnet.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [dbnet.actor :as actor]
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
        request {:client-id "client-1" :op :apply-migration :stake :high
                 :schema-id "orders"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
