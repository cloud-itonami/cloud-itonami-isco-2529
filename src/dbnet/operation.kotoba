(ns dbnet.operation
  "The closed vocabulary of operations the ISCO-08 2529 database and network
  professionals (NEC) actor may propose.

  Runtime: portable `.cljc` (pure data + pure predicates, no host interop).

  Why this namespace exists. Before it, the operation vocabulary lived in two
  places that could not disagree loudly: the README's prose, and the
  Governor's private `(= :propose-migration op)` test plus one named
  escalating op. That made the Governor a *denylist* — it bound one named op
  and admitted everything else. Measured on the pre-change tree, against a
  registered client and its registered candidate key:

      {:op :drop-production-table :effect :propose :schema-id \"orders\"
       :fds [{:determinant #{\"customer_id\"} :dependent #{\"customer_city\"}}]
       :confidence 0.95}
      => {:ok? true :violations []}

  Admitted, and admitted as a *clean* verdict: no escalation, no human, and
  an empty violation list to show a reviewer. `:grant-superuser` and
  `:delete-all-backups` were admitted the same way, and so was a proposal
  whose `:op` was `nil`.

  An actor whose operation set is open cannot be governed, because the
  governor is answering a question about a vocabulary nobody declared. So the
  vocabulary is declared here, once, as an allowlist, and `dbnet.governor`
  refuses anything outside it.

  Two disjoint maps:

  * `supported` — what the actor may propose. `:escalates?` and `:schema-op?`
    are properties of the operation, not of the governor's mood, so they live
    beside it.
  * `reserved` — operations naming authority this cognitive actor does not
    hold: destructive DDL against live data, privilege grants, and removal of
    the audit trail. These are *declared* rather than merely absent so the
    refusal can say why. An undeclared op is a vocabulary error; a reserved
    op is an authority boundary. Conflating them would let a future edit
    `supported`-list one of them by accident.

  `:schema-op?` is the field that closes the gap this repo shipped with. The
  key-basis invariant and the 3NF check were both gated on
  `(= :propose-migration op)`, so `:apply-migration` — the operation whose
  entire purpose is changing the production schema — was exempt from both.
  Measured on the pre-change tree, the SAME transitive dependency that hard
  blocks a proposal:

      {:op :propose-migration ...}  => {:hard? true  :violations [transitive-dependency]}
      {:op :apply-migration   ...}  => {:hard? false :escalate? true :violations []}

  and, with no registered candidate key at all:

      {:op :apply-migration :schema-id \"no-such-schema\" ...}
      => {:hard? false :escalate? true :violations []}

  The escalating op reached a human with an **empty violation list**, on the
  one operation whose premise is that production is about to change. Binding
  is a property of the operation, so it is declared here and the governor
  reads it, rather than the governor naming one op and forgetting the more
  dangerous one.")

(def supported
  "Operations the actor may propose.

  `:escalates?` true means human sign-off is required regardless of advisor
  confidence. `:schema-op?` true means the proposal binds to a REGISTERED
  schema, and therefore must satisfy every registered fact about it — the
  candidate-key basis and the 3NF relation between those keys and the
  proposed functional dependencies."
  {:draft-schema-review
   {:escalates? false
    :schema-op? false
    :summary "draft a normalization review for the responsible DBA (binds nothing)"}

   :propose-migration
   {:escalates? false
    :schema-op? true
    :summary "propose a schema migration for a registered schema"}

   :apply-migration
   {:escalates? true
    :schema-op? true
    :summary "apply a migration to the production schema"}

   :flag-anomaly
   {:escalates? true
    :schema-op? false
    :summary "surface a suspected data anomaly to the responsible DBA"}})

(def reserved
  "Operations reserved to someone this actor is not. Naming one in a proposal
  is a permanent hard block, never an escalation: escalation would imply a
  human could approve the *actor* doing it, and neither a DBA nor an operator
  can delegate destructive DDL, a privilege grant, or the removal of an audit
  trail to a remote cognitive actor.

  This is the machine-readable form of the scope sentence the README has
  carried since the repo was created — the advisor only proposes. Prose in a
  README does not refuse anything."
  {:drop-production-table
   {:reason "destructive DDL against live data; the decision belongs to the data owner, executed by the responsible DBA"}

   :delete-all-backups
   {:reason "destroying the recovery path is unrecoverable by construction and can never be delegated"}

   :grant-superuser
   {:reason "a privilege grant is the data owner's decision, not a schema recommendation"}

   :disable-audit-logging
   {:reason "removing the audit trail removes the evidence this actor's own governance rests on"}})

(defn supported? [op] (contains? supported op))
(defn reserved? [op] (contains? reserved op))

(defn declared?
  "True if `op` is named anywhere in this vocabulary. An op that is neither
  supported nor reserved is undeclared — the governor refuses it."
  [op]
  (or (supported? op) (reserved? op)))

(defn escalates?
  "True if the operation itself always requires human sign-off. Unsupported
  ops are never reached by this predicate (the governor hard-blocks first), so
  a false here is not an admission."
  [op]
  (boolean (get-in supported [op :escalates?])))

(defn schema-op?
  "True if the operation binds to a registered schema and must therefore
  satisfy every registered fact about it. False for undeclared and reserved
  ops, which the governor hard-blocks before this is consulted."
  [op]
  (boolean (get-in supported [op :schema-op?])))

(defn reserved-reason [op] (get-in reserved [op :reason]))
