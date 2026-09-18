package wsm.graalvm;

import org.graalvm.polyglot.Context;

/** Exact mirror contract for upstream migration_only_cond_truthy. */
public final class MigrationCondRecordContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String eval(Context context, String source) {
        return context.eval("wsm", source).toString();
    }

    private static void expect(Context context, String expected, String source, String label) {
        String actual = eval(context, source);
        require(expected.equals(actual), label + ": expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: MigrationCondRecordContract <semantic-registry.lisp>");
        }
        System.setProperty("wsm.registryPath", args[0]);

        try (Context context = Context.newBuilder("wsm").build()) {
            expect(context, "right",
                    "(cond ((atom (quote (a b))) (quote wrong)) "
                            + "((quote fallback) (quote right)))",
                    "structural-kind pair is migration-false");

            expect(context, "yes",
                    "(cond ((atom (quote a)) (quote yes)) "
                            + "((quote fallback) (quote no)))",
                    "structural-kind atom is migration-true");

            expect(context, "yes",
                    "(cond ((atom (quote ())) (quote yes)) "
                            + "((quote fallback) (quote no)))",
                    "structural-kind empty-list is migration-true");

            expect(context, "yes",
                    "(cond ((eq (quote a) (quote a)) (quote yes)) "
                            + "((quote fallback) (quote no)))",
                    "identity-relation same is migration-true");

            expect(context, "right",
                    "(cond ((eq (quote a) (quote b)) (quote wrong)) "
                            + "((quote fallback) (quote right)))",
                    "identity-relation distinct is migration-false");

            expect(context, "yes",
                    "(cond ((quote (structural-relation same)) (quote yes)) "
                            + "((quote fallback) (quote no)))",
                    "structural-relation same is migration-true");

            expect(context, "right",
                    "(cond ((quote (structural-relation distinct)) (quote wrong)) "
                            + "((quote fallback) (quote right)))",
                    "structural-relation distinct is migration-false");

            expect(context, "one",
                    "(cond (1 (quote one)) ((quote fallback) (quote no)))",
                    "exact 1 is migration-true");

            expect(context, "zero-fallback",
                    "(cond (0 (quote wrong)) "
                            + "((quote fallback) (quote zero-fallback)))",
                    "exact 0 is migration-false");

            expect(context, "canonical-pair",
                    "(cond ((atom (quote (a b))) (structural-kind pair) "
                            + "(quote canonical-pair)) "
                            + "((quote fallback) fallback (quote wrong)))",
                    "canonical three-part cond remains explicit-result");
        }

        System.out.println("MIGRATION-COND-RECORD-BRIDGE-OK");
    }
}
