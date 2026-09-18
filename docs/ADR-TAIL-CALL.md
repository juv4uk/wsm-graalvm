# ADR — Tail-call substrate mechanism

Status: Evidence phase — decision pending  
Tracking: #204  
Authority: pinned `my-lisp` tail-position semantics

## Context

The Graal substrate currently invokes a Lisp `Closure` through a Truffle
`CallTarget`. A tail call therefore enters another host-level call unless a
dedicated substrate mechanism is introduced.

The pinned `my-lisp` authority already models tail positions explicitly:
its evaluator turns a tail-position result into `EvalStep::TailCall` and keeps
evaluating through an evaluator loop. The pinned reference witness
`crates/my-lisp/tests/mccarthy.rs::tail_recursion_uses_constant_rust_stack`
uses a depth of 5,000.

This ADR deliberately records the invariant before selecting an implementation.
No LoopNode, trampoline, or call-target rewriting is admitted merely because it
looks faster.

## Witness

The downstream witness builds the same shape as the pinned reference:

- 5,000 tail calls: each function returns the next call directly;
- 5,000 non-tail calls: each function consumes the recursive result as the
  first argument to `cons`.

The expected language-level contract is:

- tail recursion eventually returns the Lisp value `done`;
- non-tail recursion remains observably non-tail and may consume host stack;
- a tail-call implementation must preserve lexical capture, ErrorKind, evaluation
  order, and ordinary non-tail recursion.

The witness records:

```
wsm_head=<exact Graal source head>
my_lisp_pin=<exact pinned authority>
depth=5000
reference=crates/my-lisp/tests/mccarthy.rs
tail=<value|stack-overflow|lisp-error|host>
non_tail=<value|stack-overflow|lisp-error|host>
```

The current lane is record-only so that a known substrate limitation does not
become a fake GREEN contract.

## Candidates to compare

### Truffle LoopNode

Represent repeated self-tail calls as an explicit loop in the Truffle AST.

Questions to settle:
- how captured lexical frames are updated;
- how arbitrary tail targets are represented;
- how ErrorKind and source boundaries propagate;
- whether mutual tail calls remain representable.

### Trampoline

Return an explicit substrate continuation/step object and iterate outside the
host call stack.

Questions to settle:
- allocation and dispatch shape;
- interaction with macros and `eval`;
- representation of arbitrary closure targets;
- preservation of observable call/evaluation order.

### Call-target rewriting / target reuse

Rewrite or route tail positions so the current host frame does not grow.

Questions to settle:
- recursion and mutual recursion;
- indirect closure calls;
- interaction with Truffle compilation and inlining;
- debuggability and lexical frame identity.

No candidate wins in this ADR yet.

## Decision gate

Before implementation:

1. keep the witness anchored to the exact pinned `my-lisp` source;
2. record current Graal JVM behavior at depth 5,000;
3. compare the candidate mechanisms against lexical frames, closures,
   ErrorKind, non-tail recursion, and Native Image behavior;
4. choose one mechanism and define a GREEN criterion;
5. only then redesign `WsmNode`/call execution.

## Non-goals

This ADR makes no performance claim. It does not turn all recursion into a loop,
and it does not redefine Lisp tail position in Java.

The semantic owner remains the pinned `my-lisp` evaluator contract.
