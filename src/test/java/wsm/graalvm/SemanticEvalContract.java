package wsm.graalvm;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;

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

            Object lambdaDatum =
                    context.eval(
                            "wsm",
                            "(eval (quote ((lambda (x) x) 43)))");
            require(
                    "43".equals(text(lambdaDatum)),
                    "1062 must compile materialized Lisp-symbol binders, got: " + lambdaDatum);

            try {
                context.eval("wsm", "(eval)");
                throw new AssertionError("1062 arity-0 must fail");
            } catch (PolyglotException error) {
                require(
                        error.getMessage().contains("Arity")
                                || error.getMessage().contains("expects 1 argument"),
                        "1062 arity must preserve language error, got: " + error.getMessage());
            }
        }

        System.out.println("SEMANTIC-EVAL-1062-CONTRACT-OK");
    }
}
