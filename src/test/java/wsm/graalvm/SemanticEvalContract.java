package wsm.graalvm;

import org.graalvm.polyglot.Context;

/** Focused contract for semantic ID 1062: EVAL in the current WSM environment. */
public final class SemanticEvalContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String text(Object value) {
        return String.valueOf(value);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: SemanticEvalContract <semantic-registry.lisp>");
        }

        System.setProperty("wsm.registryPath", args[0]);

        try (Context context = Context.newBuilder("wsm").build()) {
            Object global =
                    context.eval(
                            "wsm",
                            "(define eval-contract-global 41) "
                                    + "(eval (quote eval-contract-global))");
            require(
                    "41".equals(text(global)),
                    "1062 must see current shared globals, got: " + global);

            Object lexical =
                    context.eval(
                            "wsm",
                            "((lambda (eval-contract-local) "
                                    + "(eval (quote eval-contract-local))) 42)");
            require(
                    "42".equals(text(lexical)),
                    "1062 must see current lexical frame, got: " + lexical);

            Object datum =
                    context.eval(
                            "wsm",
                            "(eval (quote (car (quote (radio antenna)))))");
            require(
                    "radio".equals(text(datum)),
                    "1062 must execute Lisp datum without string round-trip, got: " + datum);
        }

        System.out.println("SEMANTIC-EVAL-1062-CONTRACT-OK");
    }
}
