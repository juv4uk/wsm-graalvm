# Semantic gates — authority before optimization

This repository is a substrate consumer. It does not decide which historical
my-lisp behavior is current semantic authority.

Gate precedence:

1. pinned Lisp-owned contract files under `external/my-lisp/contracts/`;
2. focused current-logic witnesses owned by those contracts;
3. implementation-independent conformance coverage, grown monotonically as
   migration debt is reconciled upstream;
4. performance/JIT policy only after semantic admission.

The distinction matters today: the pinned control contract says canonical 0007
uses explicit result equality and forbids generic truth coercion, while the
large historical `conformance.lisp` corpus still contains migration-era
truthiness rows. wsm-graalvm must not turn that disagreement into new Java
semantic authority.

`scripts/semantic-gates.sh` therefore begins with authority-precedence checks,
then consumes the real pinned registry, then exercises ID/value/mechanism/control
contracts.

A gate may be RED while implementation work is incomplete. Never weaken the
Lisp-owned contract merely to make a Graal test GREEN.
