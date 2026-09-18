package wsm.graalvm;

import org.graalvm.polyglot.Context;

/** RED/green contract for #50: earlier Lisp definitions feed later macro expansion. */
public final class SequentialMacroBootstrapContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: SequentialMacroBootstrapContract <semantic-registry.lisp>");
        }

        System.setProperty("wsm.registryPath", args[0]);

        String source = """
            (def make-quoted
              (lambda (x)
                (cons (quote quote)
                      (cons x (quote ())))))
            (defmacro wrap x
              (make-quoted x))
            (wrap radio)
            """;

        try (Context context = Context.newBuilder("wsm").build()) {
            Object result = context.eval("wsm", source);
            require("radio".equals(result.toString()),
                    "macro expansion must observe earlier Lisp binding, got: "
                            + result);
        }

        System.out.println("SEQUENTIAL-MACRO-BOOTSTRAP-CONTRACT-OK");
    }
}
