# ADR — Tail-call substrate mechanism for GraalVM

Status: proposed  
Issue: #204  
Parent: #10, #93  
Evidence PR: #210

## Context

The language meaning remains owned by pinned `my-lisp`. GraalVM must preserve that
meaning without turning host JVM stack limits into an observable language rule.

A reproducible probe now executes ordinary upstream-style Lisp recursion through the
same pinned `canon -> macro -> core` bootstrap used by the main substrate.

The probe intentionally uses only already-admitted Canon mechanisms:
`quote`, `atom`, `cons`, `cdr`, `cond`, `lambda`, and `define`.
It does not depend on unfinished arithmetic mechanisms.

## Measured baseline

GraalVM CE 25.3.4.1, GitHub-hosted Linux runner:

| mode | 64 | 256 | 1024 | 4096 | 16384 |
|---|---|---|---|---|---|
| tail recursion | value | value | host StackOverflowError | host StackOverflowError | host StackOverflowError |
| non-tail control | value | value | host StackOverflowError | host StackOverflowError | host StackOverflowError |

Observed value shapes:
- tail success -> `(structural-kind empty-list)`
- non-tail success -> `(structural-kind pair)`

The important result is qualitative: current tail recursion consumes the same host
stack class as non-tail recursion. No tail-position frame reuse is present.

`StackOverflowError` is a host failure, not a Lisp ErrorKind. Therefore this is a
substrate-parity gap rather than a new language-visible failure rule.

## Decision drivers

Any admitted mechanism must preserve:

1. lexical frame semantics and captured-parent behavior;
2. ordinary non-tail recursion unchanged;
3. exact observable values and ErrorKind behavior;
4. macro-expanded and direct lambda calls;
5. shared-context top-level definitions;
6. Native Image compatibility;
7. no Java-owned Lisp semantic shortcut;
8. evidence that deep tail recursion stops consuming one JVM frame per iteration.

## Candidate A — Truffle LoopNode for self-tail calls

Shape:
- compiler recognizes calls in lambda tail position that target the same closure;
- evaluates next arguments;
- rebinds the lambda frame state;
- continues through a Truffle `LoopNode`.

Advantages:
- maps naturally to Truffle optimization machinery;
- can remove host-frame growth for self-tail recursion;
- keeps ordinary non-tail calls on normal CallTarget paths.

Risks / questions:
- recursive top-level definitions resolve through globals, so proof is needed that
  self-target identity is stable enough for safe lowering;
- lexical captures and nested lambdas must not be accidentally rebound;
- mutual tail recursion is not solved by a self-tail-only loop.

## Candidate B — trampoline

Shape:
- tail-position calls return an internal substrate continuation/request;
- a dispatcher repeatedly executes those requests without growing the JVM stack.

Advantages:
- can generalize to mutual tail recursion;
- does not require every tail target to be statically identical.

Risks / questions:
- introduces an internal control protocol across call boundaries;
- must remain completely non-observable to Lisp;
- every call path must distinguish ordinary values from internal trampoline state;
- may complicate Truffle partial evaluation and Native Image behavior.

## Candidate C — CallTarget / frame reuse

Shape:
- reuse or replace the current call target/frame on tail calls without a separate
  trampoline object protocol.

Advantages:
- potentially preserves the current call architecture closely.

Risks / questions:
- Truffle does not provide a generic JVM tail-call guarantee merely from
  `CallTarget.call`;
- implementation evidence is required before assuming host-frame elimination;
- captured frame chains make unsafe frame mutation particularly risky.

## Proposed evaluation order

1. Keep #210 as the permanent pre-implementation baseline.
2. Prototype self-tail `LoopNode` on a branch with the exact same probe.
3. Require:
   - tail 16384 -> value;
   - non-tail 1024+ may still host-overflow and must remain semantically unchanged;
   - existing m0, shared-session, and Native Image lanes GREEN.
4. If LoopNode cannot cover required tail shapes without semantic special cases,
   prototype trampoline next.
5. Do not broaden to mutual-tail optimization until a separate witness exists.

## Non-decision

This ADR does **not** choose a mechanism yet. The measured baseline is accepted;
the implementation choice remains pending prototype evidence.

## Rejection rule

Reject any approach that:
- changes Lisp source to avoid recursion;
- catches `StackOverflowError` and maps it to a Lisp ErrorKind;
- adds a Java semantic definition of the recursive function;
- makes tail recursion green by reducing the witness depth;
- changes non-tail semantics merely to share an optimization path.
