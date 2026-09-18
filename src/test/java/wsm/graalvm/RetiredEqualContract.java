package wsm.graalvm;

import java.nio.file.Path;
import org.graalvm.polyglot.Context;

/** Proves 1022/equal? is supplied by pinned Lisp core, not Java. */
public final class RetiredEqualContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: RetiredEqualContract <wsm-graalvm-root>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        Path registry = repo.resolve(
                "external/my-lisp/lib/surface/semantic-registry.lisp").normalize();

        require(
                !SemanticMechanismTable.supports("1022"),
                "Java mechanism 1022 must be retired");

        System.setProperty("wsm.registryPath", registry.toString());

        try (Context context = Context.newBuilder("wsm").build()) {
            Path authority = repo.resolve("external/my-lisp");
            String[] bootstrap = {
                "lib/canon.lisp",
                "lib/macro.lisp",
                "lib/core.lisp"
            };

            for (String relative : bootstrap) {
                context.eval(
                        "wsm",
                        java.nio.file.Files.readString(authority.resolve(relative)));
            }

            Object result = context.eval(
                    "wsm",
                    "(equal? (quote (radio antenna)) (quote (radio antenna)))");

            require(
                    "(structural-relation same)".equals(result.toString()),
                    "Lisp-owned equal? must remain executable after Java 1022 retirement, got: "
                            + result);
        }

        System.out.println("RETIRED-EQUAL-1022-CONTRACT-OK");
    }
}
