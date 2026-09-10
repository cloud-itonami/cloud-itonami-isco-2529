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
  (testing "a production migration escalates ONLY once it has passed the schema
  checks. This test used to pass a proposal with no :fds at all and assert
  {:hard? false :escalate? true} — that is, it asserted the defect: the
  operation that changes the production schema reaching a human with an empty
  violation list. The FD set is the evidence the migration offers for its own
  normalization, so it is now required, and the escalation is what is left
  after the checks pass rather than instead of them."
    (let [st (fresh-store)
          v (governor/check req {} {:op :apply-migration :effect :propose
                                    :schema-id "orders" :confidence 0.9 :stake :high
                                    :fds [{:determinant #{"order-id"}
                                           :dependent #{"customer-id"}}]} st)]
      (is (not (:hard? v)))
      (is (:escalate? v))))
  (testing "and the same operation with no FDs declared now HOLDS"
    (let [st (fresh-store)
          v (governor/check req {} {:op :apply-migration :effect :propose
                                    :schema-id "orders" :confidence 0.9 :stake :high} st)]
      (is (:hard? v))
      (is (some #(= :no-fds-declared (:rule %)) (:violations v))))))

(deftest confidence-floor-boundary
  (testing "exactly at the floor is NOT low — the comparison is `<`, so the
  boundary case is the only input that distinguishes `<` from `<=`. Without a
  case sitting exactly on the line, flipping that operator leaves every test
  green."
    (let [st (fresh-store)
          at-floor (governor/check req {} (assoc (migration [{:determinant #{"order-id"}
                                                              :dependent #{"customer-id"}}])
                                                 :confidence governor/confidence-floor) st)
          below (governor/check req {} (assoc (migration [{:determinant #{"order-id"}
                                                           :dependent #{"customer-id"}}])
                                              :confidence 0.5999) st)]
      (is (:ok? at-floor))
      (is (not (:escalate? at-floor)))
      (is (:escalate? below)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (migration [{:determinant #{"order-id"}
                                                     :dependent #{"customer-id"}}])
                                        :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
