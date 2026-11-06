# ADR-005: Rules-first, explainable fraud score with bounded AI input

**Status:** accepted

## Context
Insurers have to justify why a claim was delayed or rejected. A pure ML/LLM score ("78% fraud") isn't acceptable to a regulator or a customer, and LLMs can hallucinate.

## Decision
- The score is computed by `FraudScorer`, a set of deterministic rules. Each rule adds points **and a sentence explaining why**: amount close to the sum insured, a policy only days old, late reporting, repeat claims, a document reused across claims, a bill total that doesn't match the claimed amount.
- The LLM is used for what it's good at: reading messy documents to pull out the bill total, provider and items, writing a 3-line summary, and spotting inconsistencies in the story. Its contribution to the score is **capped at 20 points**, and its reasons are shown prefixed with `AI:`.
- The score never makes a decision. It only routes: claims scoring ≥ 70 can't be approved by an adjuster alone.

## Consequences
- ✅ Every score is reproducible and explainable, and the rules have unit tests.
- ✅ The system works fully without an LLM.
- ❌ The rules need tuning with real data. Weights live in one class to make that easy.
