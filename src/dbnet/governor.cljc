(ns dbnet.governor
  "DatabaseNetworkProfessionalsGovernor — the independent safety/
  traceability layer for the ISCO-08 2529 community database & network
  professionals (NEC) actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section). Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Database twist: 3NF is a relation between the
  registered candidate keys and the proposed functional dependencies —
  a non-trivial FD whose determinant is not a superkey AND whose
  dependent has an attribute outside every candidate key is a
  transitive dependency, detected by set membership, not judged by
  taste.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. key basis         — a migration must cite a schema with at
                           least one REGISTERED candidate key for this
                           client (no undefined schema).
    4. 3NF violation      — for every proposed FD, either the
                           determinant is a superkey (superset of some
                           registered candidate key) or every dependent
                           attribute belongs to some candidate key;
                           otherwise it is a transitive dependency.
  ESCALATION invariants (:escalate? true, human sign-off):
    5. :op :apply-migration (production schema change).
    6. low confidence (< `confidence-floor`)."
  (:require [clojure.set :as set]
            [dbnet.store :as store]))

(def confidence-floor 0.6)

(defn- superkey? [keys attrs]
  (some #(set/subset? (:attrs %) attrs) keys))

(defn- prime-attr? [keys attr]
  (some #(contains? (:attrs %) attr) keys))

(defn- fd-violation [keys {:keys [determinant dependent]}]
  (let [det (set determinant)
        dep (set dependent)
        trivial? (set/subset? dep det)]
    (when (and (not trivial?)
               (not (superkey? keys det))
               (not (every? #(prime-attr? keys %) dep)))
      {:rule :transitive-dependency
       :detail (str "FD " det " -> " dep " は determinant が superkey で"
                    "なく dependent も候補キー外の属性を含む（推移的従属"
                    "は集合帰属で機械検出できる。設計の好みではない）")})))

(defn- hard-violations [{:keys [request proposal]} client-record keys]
  (let [{:keys [op fds]} proposal
        migrate? (= :propose-migration op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and migrate? (empty? keys))
      (conj {:rule :no-registered-keys
             :detail "schema に登録済み候補キーが無い（未定義 schema）"})

      (and migrate? (seq keys))
      (into (keep #(fd-violation keys %) fds)))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `dbnet.store/Store`. Pure — never mutates the
  store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        keys (store/keys-of store (:client-id request) (:schema-id proposal))
        hard (hard-violations {:request request :proposal proposal}
                              client-record keys)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :apply-migration (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
