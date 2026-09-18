package wsm.graalvm;

import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;

/**
 * Focused contract for manifest-driven bootstrap transport from external/my-lisp.
 */
public final class BootstrapClosureLoaderContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String valueText(Object value) {
        if (value instanceof Value.StringValue text) return text.value;
        return String.valueOf(value);
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
                        authority.resolve("lib/core.lisp").normalize(),
                        authority.resolve("lib/macro.lisp").normalize());

        require(
                executable.equals(expected),
                "manifest executable load order mismatch: " + executable);

        for (BootstrapClosureLoader.Source source : closure.executableSources()) {
            require(
                    source.path().startsWith(authority),
                    "bootstrap source escaped external/my-lisp: " + source.path());
            require(!source.text().isBlank(), "bootstrap source is empty: " + source.path());
        }

        System.setProperty("wsm.registryPath", closure.registryPath().toString());
        try (Context context = Context.newBuilder("wsm").build()) {
            for (BootstrapClosureLoader.Source source : closure.executableSources()) {
                context.eval("wsm", source.text());
            }

            Object canon = context.eval("wsm", "(canon-conforms?)");
            require(
                    "(canon-conformance satisfied)".equals(valueText(canon)),
                    "manifest bootstrap must preserve Canon witness, got: " + canon);

            Object identity = context.eval("wsm", "(identity 42)");
            require(
                    "42".equals(valueText(identity)),
                    "current upstream core identity must execute, got: " + identity);

            Object macroResult = context.eval(
                    "wsm",
                    "(define constructor make-macro) "
                            + "(define identity-macro (constructor (lambda (x) x))) "
                            + "(identity-macro 42)");
            require(
                    "42".equals(valueText(macroResult)),
                    "make-macro must turn a Closure into a usable first-class macro");

            try {
                context.eval("wsm", "(constructor)");
                throw new AssertionError("make-macro zero-argument call must fail with Arity");
            } catch (org.graalvm.polyglot.PolyglotException error) {
                require(
                        error.getMessage().contains("Arity"),
                        "wrong error kind for make-macro arity: " + error.getMessage());
            }

            try {
                context.eval("wsm", "(constructor 42)");
                throw new AssertionError("make-macro non-closure call must fail with Type");
            } catch (org.graalvm.polyglot.PolyglotException error) {
                require(
                        error.getMessage().contains("Type"),
                        "wrong error kind for make-macro type: " + error.getMessage());
            }
        }

        System.out.println("BOOTSTRAP-CLOSURE-LOADER-OK");
    }
}
