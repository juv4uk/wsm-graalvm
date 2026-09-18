# ADR-001 — ID-first semantic values on GraalVM

Status: proposed / RED contract for #2, #3, #4

## Decision

`wsm-graalvm` does not define Lisp callable identity with Java function
objects, Java class names, source spellings, or a duplicate enum.

The language-owned semantic identity is the numeric ID from the pinned
`my-lisp` semantic registry.

All admitted surfaces for one callable identity, including the machine ID
itself, materialize the same language value:

```
car / перше / ādi / 0005
          ↓
  SemanticRef("0005")
```

A surface spelling is input syntax/data for resolution only. After resolution,
runtime execution must not consult the spelling again.

## Identity vs mechanism boundary

Language identity:

```
SemanticRef("0005")
```

Graal mechanism:

```
invoke("0005", args)
    ↓
mechanism table
    ↓
Truffle implementation
```

The mechanism table may specialize, inline, cache, or lower an already-resolved
ID. It may not invent or replace the semantic identity.

## Non-goals

- No `CarValue`, `CdrValue`, `JavaCarBuiltin`, or equivalent object class
  becomes the language value.
- No English/Ukrainian/Sanskrit spelling is hardcoded into runtime dispatch.
- No arbitrary user-level quoted symbol is forced through the semantic registry.
- Special forms remain syntax-only where the upstream language contract says so.

## Required observable properties

1. Every admitted surface for one ID produces semantically equal
   `SemanticRef(ID)` values.
2. Re-resolving the same ID must not depend on Java object allocation identity.
3. The numeric machine ID itself resolves to the same value as its admitted
   human/symbolic surfaces.
4. Invocation dispatch is keyed by ID only.
5. A future Truffle optimization may replace the mechanism, but never the
   observable identity.

## First RED witness

`tests/SemanticIdentityContract.java` intentionally fails against the current
M0 because `Value.SemanticRef` does not exist and callable values are still
materialized as `WsmFunc` instances.

Do not weaken this witness to make M0 green. Change the implementation.

## Upstream authority

This ADR describes the Graal substrate boundary only. The meanings attached to
IDs and the admitted spellings belong to the pinned `my-lisp` authority and
must be consumed from there, not restated here.
