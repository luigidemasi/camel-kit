# Shared discovery and completeness semantics

Use these semantics for both standalone `camel-brainstorm` interviews and controller-bounded Ship discovery.

1. Analyze every supplied requirements source before asking the first question. Separate facts, explicit decisions,
   conflicting claims, assumptions, recommendations, and material unknowns. Preserve where each item came from.
2. A conclusively resolved item is satisfied; do not ask it again just because it appears in an interview template.
   A complete requirements document may therefore need zero clarification questions.
3. Never leave a required category unresolved. Record it as resolved, open, or not applicable with a concrete
   rationale. Do not turn an assumption, recommendation, familiar framework, or local default into a user decision.
4. Ask exactly one highest-priority blocking question at a time. Record the answer before recomputing the remaining
   gaps. Useful options and a recommendation are welcome, but free-form answers and explicit not-applicable answers
   remain valid.
5. Cover business purpose, actors and flows, triggers/sources/destinations, message contracts and transformations,
   routing rules, runtime and versions, deployment, route DSL and expression language, Java policy, failure handling,
   security, observability, acceptance criteria, required integration tests, the exact Citrus release, and the
   exact Citrus dependency set approved for the workflow.
6. Resolve runtime, Camel version, and applicable platform versions before using runtime-sensitive Camel catalog
   results. A later runtime/version change invalidates those results.
7. Discovery completion means no blocking ambiguity, conflict, unconfirmed assumption, or untaken material decision
   remains. It does not waive presentation and approval of the exact resulting design.

Standalone Brainstorm persists these semantics in its requirements/design artifacts. Ship workers return the typed
ledger requested by the controller and never own persistence, approval, stage transitions, or implementation.
