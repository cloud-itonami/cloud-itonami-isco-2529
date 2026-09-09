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

  The invariant that binds the whole thing: **a check is never skipped
  silently.** Where a comparison cannot be made, `dbnet.facts` has already
  recorded why as a violation of its own, so an unusable value produces a
  refusal rather than an admission.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. vocabulary        — the op must be declared in
                           `dbnet.operation/supported`. A reserved op
                           (destructive DDL, privilege grant, removal of the
                           audit trail) is reported separately from an
                           undeclared one.
    2. client provenance — the store's record must identify the client the
                           request names.
    3. no-actuation      — proposal :effect must be :propose.
    4. schema basis      — an operation that binds to a schema must cite one,
                           must declare a readable non-empty FD set, and that
                           schema must have at least one REGISTERED candidate
                           key (no undefined schema).
    5. registered record — every registered candidate key must itself carry a
                           usable attribute set.
    6. 3NF violation     — for every proposed FD, either the determinant is a
                           superkey (superset of some registered candidate
                           key) or every dependent attribute belongs to some
                           candidate key; otherwise it is a transitive
                           dependency.
    7. usable confidence — a present :confidence must be a number in [0,1].
  ESCALATION invariants (:escalate? true, human sign-off):
    8. the operation declares `:escalates?` — currently :apply-migration
                           (production schema change) and :flag-anomaly.
    9. low confidence (< `confidence-floor`).

  Invariants 4, 5 and 6 now apply to EVERY schema-binding operation, not only
  to `:propose-migration`. That is the substantive change: an
  `:apply-migration` used to reach a human with an empty violation list."
  (:require [clojure.set :as set]
            [dbnet.facts :as facts]
            [dbnet.operation :as op]
            [dbnet.store :as store]))

(def confidence-floor 0.6)

(defn- superkey? [keys attrs]
  (some #(set/subset? (set (:attrs %)) attrs) keys))

(defn- prime-attr? [keys attr]
  (some #(contains? (set (:attrs %)) attr) keys))

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

(defn- schema-invariant-violations
  "The normalization checks proper, for operations that bind to a registered
  schema.

  Each check is guarded by the values being usable, but the guard is not a
  silent skip: `facts/schema-binding-violations` and
  `facts/key-record-violations` have already reported an unusable value as a
  violation, so a guarded-out check never turns into an admission. That
  pairing is the whole reason those namespaces exist."
  [proposal keys]
  (let [{:keys [op fds]} proposal]
    (if-not (op/schema-op? op)
      []
      (cond-> []
        (empty? keys)
        (conj {:rule :no-registered-keys
               :detail "schema に登録済み候補キーが無い（未定義 schema）"})

        ;; Only compare against keys that are themselves usable. An unusable
        ;; key has already been reported by facts/key-record-violations, so
        ;; this is a refusal either way — never an admission.
        (and (seq keys)
             (every? #(facts/attr-set? (:attrs %)) keys)
             (coll? fds))
        (into (keep #(when (facts/well-formed-fd? %) (fd-violation keys %)) fds))))))

(defn- hard-violations [request proposal client-record keys]
  (vec (concat (facts/vocabulary-violations proposal)
               (facts/provenance-violations request client-record)
               (when (not= :propose (:effect proposal))
                 [{:rule :no-actuation
                   :detail "effect は :propose のみ許可（直接書込禁止）"}])
               (facts/schema-binding-violations proposal)
               (facts/key-record-violations (:op proposal) keys)
               (facts/confidence-violations proposal)
               (schema-invariant-violations proposal keys))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `dbnet.store/Store`. Pure — never mutates the
  store.

  Returns `{:ok? :violations :confidence :hard? :escalate?}`. `:hard?` is
  checked before `:escalate?` by every caller (see `dbnet.phase`): a proposal
  that is both hard-blocked and escalating must hold, because escalating it
  would ask a human to approve something no human may approve."
  [request _context proposal store]
  (let [client-record (store/client store (:client-id request))
        keys (store/keys-of store (:client-id request) (:schema-id proposal))
        hard (hard-violations request proposal client-record keys)
        hard? (boolean (seq hard))
        raw-conf (:confidence proposal)
        conf (if (facts/usable-confidence? raw-conf) raw-conf 0.0)
        low? (< conf confidence-floor)
        risky-op? (op/escalates? (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
