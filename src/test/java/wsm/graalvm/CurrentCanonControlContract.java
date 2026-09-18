package wsm.graalvm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical control contract:
 * COND clauses are only (query expected-result expression).
 *
 * The expected result is reader data, not executable code. Selection is
 * structural equality; there is no generic truth coercion in CondNode.
 */
public final class CurrentCanonControlContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static CanonRegistry registry() {
        Map<String, CanonRegistry.Row> rows = new LinkedHashMap<>();
        for (String id : new String[] {"0002", "0004", "0007"}) {
            rows.put(id, new CanonRegistry.Row(id, Map.of()));
        }
        return CanonRegistry.registry(rows, Map.of());
    }

    private static Object eval(String source) {
        Compiler compiler = new Compiler(registry());
        Object form = new Reader(source).readAll().get(0);
        return compiler.compile(form, compiler.root()).executeGeneric(null);
    }

    public static void main(String[] args) {
        Object pairMatch = eval(
                "(0007 ((0002 (0004 1 2)) (structural-kind pair) 42))");
        require(
                pairMatch instanceof Value.NumberValue n
                        && n.numerator().longValueExact() == 42L
                        && n.denominator().intValueExact() == 1,
                "explicit structural-kind result must select branch");

        Object noMatch = eval(
                "(0007 ((0002 (0004 1 2)) (structural-kind atom) 42))");
        require(noMatch == Value.NIL,
                "non-matching canonical clause must return empty list");

        Object emptyMatched = eval("(0007 (() () 7))");
        require(
                emptyMatched instanceof Value.NumberValue n
                        && n.numerator().longValueExact() == 7L
                        && n.denominator().intValueExact() == 1,
                "empty list must be matchable explicitly as data");

        try {
            eval("(0007 ((0002 (0004 1 2)) 42))");
            throw new AssertionError(
                    "historical two-part cond must not define core semantics");
        } catch (WsmError error) {
            require(
                    error.kind == WsmError.Kind.INVALID_FORM,
                    "two-part core cond must fail as InvalidForm, got " + error.kind);
        }

        Object expectedDatum =
                ReaderDatum.toValue(
                        new Reader("(identity-relation same)").readAll().get(0));
        require(
                Printer.print(expectedDatum).equals("(identity-relation same)"),
                "expected result must be Lisp data, not Reader.Token objects");

        Object zeroMatch = eval("(0007 (0 0 7))");
        require(
                zeroMatch instanceof Value.NumberValue n
                        && n.numerator().longValueExact() == 7L
                        && n.denominator().intValueExact() == 1,
                "ordinary exact zero is data and matches only explicit exact zero");

        Object zeroNoMatch = eval("(0007 (0 1 7))");
        require(zeroNoMatch == Value.NIL,
                "ordinary exact zero must not be coerced to truth/false");

        System.out.println("CURRENT-CANON-CONTROL-CONTRACT-OK");
    }
}
