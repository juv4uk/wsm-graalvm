package wsm.graalvm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * #5 contract: new Graal substrate starts from current #217 control semantics.
 */
public final class CurrentCanonControlContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static CanonRegistry registry() {
        Map<String, CanonRegistry.Row> rows = new LinkedHashMap<>();
        for (String id : new String[]{"0002", "0004", "0007"}) {
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
        require(pairMatch instanceof Long n && n == 42L,
                "explicit structural-kind result must select branch");

        Object noMatch = eval(
                "(0007 ((0002 (0004 1 2)) (structural-kind atom) 42))");
        require(noMatch == Value.NIL,
                "no matching canonical clause must return empty list");

        Object emptyMatched = eval("(0007 (() () 7))");
        require(emptyMatched instanceof Long n && n == 7L,
                "empty list must be matchable as data, not coerced to false");

        try {
            eval("(0007 ((0002 (0004 1 2)) 42))");
            throw new AssertionError("historical two-part cond must not define core semantics");
        } catch (WsmError error) {
            require(error.kind == WsmError.Kind.INVALID_FORM,
                    "two-part core cond must fail as InvalidForm, got " + error.kind);
        }

        Object expectedDatum = ReaderDatum.toValue(
                new Reader("(identity-relation same)").readAll().get(0));
        require(Printer.print(expectedDatum).equals("(identity-relation same)"),
                "expected result must be Lisp data, not Reader.Token objects");

        System.out.println("CURRENT-CANON-CONTROL-CONTRACT-OK");
    }
}
