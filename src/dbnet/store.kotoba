(ns dbnet.store
  "SSoT for the ISCO-08 2529 community database & network
  professionals (NEC) actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section). Modeled on cloud-itonami-isco-4311's
  bookkeeping.store.

  Domain:

    client       — a registered organization (:client-id, :name)
    key          — a registered candidate key of a schema
                   {:key-id :client-id :schema-id :attrs #{attr-str}}
    dependency   — a registered functional dependency (FD)
                   {:dep-id :client-id :schema-id
                    :determinant #{attr-str} :dependent #{attr-str}}.
                   Normalization is a relation between the registered
                   keys and the registered FDs — a schema is in 3NF
                   (per this actor's check) iff every non-trivial FD's
                   determinant is a superkey OR every dependent
                   attribute is part of some candidate key. A
                   violation is a set-membership fact, not a design
                   opinion.
    record       — a committed operating record (approved schema
                   migration) — written ONLY via commit-record!.
    ledger       — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (keys-of [s client-id schema-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-key! [s k])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (keys-of [_ client-id schema-id]
    (filter #(and (= client-id (:client-id %)) (= schema-id (:schema-id %)))
            (vals (:keys @a))))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-key! [s k]
    (swap! a assoc-in [:keys (:key-id k)] k) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :keys {} :records [] :ledger []}
                                   seed)))))
