(ns dbnet.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [dbnet.store :as store]
            [dbnet.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-key! st {:key-id "K-1" :client-id "client-1"
                             :schema-id "orders" :attrs #{"order-id"}})
    st))

(defn- migration [fds]
  {:op :propose-migration :effect :propose :schema-id "orders" :fds fds
   :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-fd-from-superkey
  (let [st (fresh-store)
        v (governor/check req {} (migration [{:determinant #{"order-id"}
                                              :dependent #{"customer-id"}}]) st)]
    (is (:ok? v))))

(deftest ok-trivial-fd
  (testing "a subset dependent is trivial, never a violation"
    (let [st (fresh-store)
          v (governor/check req {} (migration [{:determinant #{"order-id" "customer-id"}
                                                :dependent #{"order-id"}}]) st)]
      (is (:ok? v)))))

(deftest ok-dependent-is-prime-attr
  (testing "a dependent that is itself part of a candidate key is not transitive"
    (let [st (fresh-store)]
      (store/register-key! st {:key-id "K-2" :client-id "client-1"
                               :schema-id "orders" :attrs #{"customer-id" "sku"}})
      (let [v (governor/check req {} (migration [{:determinant #{"sku"}
                                                  :dependent #{"customer-id"}}]) st)]
        (is (:ok? v))))))

(deftest hard-on-transitive-dependency
  (testing "transitive dependency is set membership, not taste"
    (let [st (fresh-store)]
      (store/register-key! st {:key-id "K-2" :client-id "client-1"
                               :schema-id "orders" :attrs #{"order-id" "line-no"}})
      (let [v (governor/check req {} (assoc (migration [{:determinant #{"customer-id"}
                                                         :dependent #{"customer-name"}}])
                                            :confidence 0.99) st)]
        (is (:hard? v))
        (is (some #(= :transitive-dependency (:rule %)) (:violations v)))))))

(deftest hard-on-no-registered-keys
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (let [v (governor/check req {} (migration [{:determinant #{"a"} :dependent #{"b"}}]) st)]
      (is (:hard? v))
      (is (some #(= :no-registered-keys (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (migration []) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (migration [])
                                        :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-migration-apply
  (let [st (fresh-store)
        v (governor/check req {} {:op :apply-migration :effect :propose
                                  :schema-id "orders" :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (migration [{:determinant #{"order-id"}
                                                     :dependent #{"customer-id"}}])
                                        :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
