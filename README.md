# cloud-itonami-isco-2529

Open Business Blueprint for **ISCO-08 2529**: Database and Network Professionals NEC — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — DatabaseNetworkProfessionalsAdvisor ⊣
DatabaseNetworkProfessionalsGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
12 tests / 24 assertions green.

The schema HARD invariant — 3NF as a relation between registered
candidate keys and proposed functional dependencies:

1. **Transitive-dependency detection** — a non-trivial FD is only
   admissible if its determinant is a superkey (superset of some
   registered candidate key) or every dependent attribute is itself
   part of some candidate key. Otherwise it is a transitive
   dependency, detected by set membership, not design taste.
2. **Key basis** — a migration must target a schema with at least one
   registered candidate key (no undefined schema).

Also HARD: unregistered organization, non-`:propose` effect.
Escalations (always human sign-off): `:apply-migration` (production
schema change), low confidence (< 0.6).



AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
