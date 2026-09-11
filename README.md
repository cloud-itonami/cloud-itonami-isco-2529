# cloud-itonami-isco-2529

Open Business Blueprint for **ISCO-08 2529**: Database and Network Professionals NEC — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — DatabaseNetworkProfessionalsAdvisor ⊣
DatabaseNetworkProfessionalsGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.

## What it refuses

The schema HARD invariant — 3NF as a relation between registered
candidate keys and proposed functional dependencies:

1. **Transitive-dependency detection** — a non-trivial FD is only
   admissible if its determinant is a superkey (superset of some
   registered candidate key) or every dependent attribute is itself
   part of some candidate key. Otherwise it is a transitive
   dependency, detected by set membership, not design taste.
2. **Key basis** — a migration must target a schema with at least one
   registered candidate key (no undefined schema), and every
   registered key must itself carry a usable attribute set.

Also HARD: an operation outside the declared vocabulary, an operation
reserved to someone this actor is not, unregistered or unidentified
organization, a non-`:propose` effect, a schema-binding operation that
declares no functional dependencies, an unreadable FD, and a present
but unusable `:confidence`.

Escalations (always human sign-off): `:apply-migration` (production
schema change), low confidence (< 0.6).

## Layout

    src/dbnet/operation.kotoba  the closed operation vocabulary (allowlist + reserved)
    src/dbnet/facts.kotoba      well-formedness of client, key and proposal values
    src/dbnet/governor.kotoba   the independent safety layer
    src/dbnet/phase.kotoba      verdict -> phase, and what each phase may do
    src/dbnet/ledger.kotoba     append-only audit ledger, hash-chained
    src/dbnet/store.kotoba      SSoT
    src/dbnet/advisor.kotoba    proposes only (mock / LLM)
    src/dbnet/actor.kotoba      the wired StateGraph
    src/dbnet/sim.kotoba        governed-scenario harness

## Verifying it

    kbb -M:test    # 55 tests, 210 assertions
    kbb -M:sim     # 18 scenarios, 17 refusals; exits 1 if refusals = 0
    kbb -M:lint

`:sim` is separate from `:test` because it asserts something the unit
tests cannot: that the **wired graph** refuses, that an escalated
request interrupts rather than writing, and that the ledger left behind
verifies. A run that demonstrates zero refusals is reported as a
failure, not a pass — an actor whose claim is "there exist actions it
refuses" has shown nothing until one is shown.

Each namespace's docstring records the measurement on the pre-change
tree that motivated it. In summary, against a registered client and its
registered candidate key, the governor previously admitted with
`{:ok? true :violations []}`:

* `:drop-production-table`, `:grant-superuser`, `:delete-all-backups`
  and a `nil` op — the operation vocabulary was a denylist, so it bound
  `:propose-migration` and admitted everything else.
* a migration declaring no functional dependencies at all, and one
  whose registered candidate key had no attributes — `subset?` of the
  empty set is true for every determinant, so one such key turned the
  3NF check off for the whole schema.
* a `:confidence` of `99.0`, which bought the advisor out of
  escalation.

and escalated to a human with an **empty violation list**:

* `:apply-migration` carrying the same transitive dependency that hard
  blocks `:propose-migration`, and `:apply-migration` against a schema
  with no registered candidate key. The key-basis and 3NF checks were
  both gated on `(= :propose-migration op)`, exempting the one
  operation that changes the production schema. Binding is now a
  declared property of the operation (`:schema-op?`), so the governor
  reads it instead of naming one op and forgetting the other.

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
