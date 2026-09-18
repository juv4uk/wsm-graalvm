package wsm.graalvm;

import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;

/** Transport contract while current-source execution mechanisms land separately. */
public final class BootstrapClosureLoaderContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: BootstrapClosureLoaderContract <wsm-graalvm-root>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        Path authority = repo.resolve("external/my-lisp").normalize();
        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);

        require(
                closure.registryPath().equals(
                        authority.resolve("lib/surface/semantic-registry.lisp").normalize()),
                "registry must come from pinned external/my-lisp authority");

        List<Path> executable =
                closure.executableSources().stream()
                        .map(BootstrapClosureLoader.Source::path)
                        .toList();

        List<Path> expected =
                List.of(
                        authority.resolve("lib/canon.lisp").normalize(),
                        authority.resolve("lib/macro.lisp").normalize(),
                        authority.resolve("lib/core.lisp").normalize());

        require(executable.equals(expected),
                "manifest executable load order mismatch: " + executable);

        for (BootstrapClosureLoader.Source source : closure.executableSources()) {
            require(source.path().startsWith(authority),
                    "bootstrap source escaped external/my-lisp: " + source.path());
            require(!source.text().isBlank(),
                    "bootstrap source is empty: " + source.path());
        }

        System.setProperty("wsm.registryPath", closure.registryPath().toString());
        try (Context context = Context.newBuilder("wsm").build()) {
            for (BootstrapClosureLoader.Source source : closure.executableSources()) {
                context.eval("wsm", source.text());
            }

            Object identity = context.eval("wsm", "(identity 42)");
            require("42".equals(identity.toString()),
                    "pinned lib/core.lisp identity must execute, got: " + identity);

            Object structural =
                    context.eval(
                            "wsm",
                            "(write-to-string "
                                    + "(equal? (quote (radio antenna)) "
                                    + "(quote (radio antenna))))");
            require("(structural-relation same)".equals(structural.toString()),
                    "pinned equal? must match current structural result contract, got: "
                            + structural);

            Object ordering = context.eval("wsm", "(<= 1 2 2 3)");
            require("1".equals(ordering.toString()),
                    "pinned lib/core.lisp <= must execute without Java mechanism, got: "
                            + ordering);
        }

        System.out.println("BOOTSTRAP-CLOSURE-EXECUTION-OK");
    }
}
