package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Focused #205 witness: Java Pair identity and Lisp structural equality stay
 * separate observable mechanisms.
 */
public final class PairRepresentationContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path repo = Path.of(args.length == 1 ? args[0] : ".")
                .toAbsolutePath().normalize();

        Value.Pair left = new Value.Pair(
                Value.symbol("x"),
                new Value.Pair(Value.NumberValue.integer(java.math.BigInteger.ONE), Value.NIL));
        Value.Pair right = new Value.Pair(
                Value.symbol("x"),
                new Value.Pair(Value.NumberValue.integer(java.math.BigInteger.ONE), Value.NIL));

        require(left != right,
                "separately allocated Pair values must have distinct Java identity");
        require(!left.equals(right),
                "Pair must not gain Java structural equals semantics");
        require(left.hashCode() != right.hashCode() || left.hashCode() == System.identityHashCode(left),
                "Pair hashCode must remain identity-based");

        require(WsmNode.Structural.equals(left, right),
                "explicit structural comparator must match equivalent Pair data");

        try {
            WsmNode.EqNode.eqRecord(left, right);
            throw new AssertionError("0003 must reject Pair operands as non-atoms");
        } catch (WsmError error) {
            require(error.contractKind().equals("Type"),
                    "Pair passed to atom-only 0003 must fail with Type");
        }

        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);
        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();
        BootstrapRuntime.executeAuthoritySource(
                context,
                Files.readString(repo.resolve("external/my-lisp/lib/canon.lisp")));

        Object result = BootstrapRuntime.execute(
                context,
                "(cond ((quote (1 2)) (1 2) (quote matched))"
                        + " (('never-selected) (1 2) (quote wrong)))");
        require("matched".equals(Printer.print(result)),
                "canonical COND must compare pair data structurally");

        System.out.println("PAIR-REPRESENTATION-CONTRACT-OK");
    }
}
