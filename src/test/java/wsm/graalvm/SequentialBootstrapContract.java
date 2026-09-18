package wsm.graalvm;

import org.graalvm.polyglot.Context;

/**
 * Top-level source order is semantic for bootstrap: an earlier Lisp definition
 * must be executable before a later macro expansion depends on it.
 */
public final class SequentialBootstrapContract {
    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: SequentialBootstrapContract <semantic-registry.lisp>");
        }

        System.setProperty("wsm.registryPath", args[0]);
        String source = """
            (def make-quote
              (lambda (x)
                (cons (quote quote)
                      (cons x (quote ())))))
            (defmacro delayed-quote (x)
              (make-quote x))
            (delayed-quote radio)
            """;

        try (Context context = Context.newBuilder("wsm").build()) {
            context.eval("wsm", source);
        }

        System.out.println("SEQUENTIAL-BOOTSTRAP-CONTRACT-OK");
    }
}
