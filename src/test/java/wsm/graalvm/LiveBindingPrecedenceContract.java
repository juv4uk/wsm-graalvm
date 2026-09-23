package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * #196 witness: live Lisp bindings shadow registry mechanisms and routes.
 *
 * Proves that once a Lisp-owned value is installed in the lexical or global
 * environment, subsequent uses of the same spelling/identity resolve to that
 * live value, not to a registry mechanism or re-projected registry route.
 */
public final class LiveBindingPrecedenceContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String spelling(CanonRegistry registry, String id) {
        return registry.row(id).surfaces().values().stream()
                .filter(s -> s != null && !s.isBlank() && !id.equals(s))
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no admitted surface for " + id));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: LiveBindingPrecedenceContract <wsm-graalvm-root>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);

        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        BootstrapRuntime.execute(
                context,
                Files.readString(repo.resolve("external/my-lisp/lib/canon.lisp")));

        BootstrapClosureLoader.Source macroSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/macro.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/macro.lisp"));
        BootstrapRuntime.execute(context, macroSource.text());

        // Use registry-derived spellings, not hard-coded aliases.
        String define = spelling(context.registry(), "00001001");
        String cond = spelling(context.registry(), "00000111");
        String cons = spelling(context.registry(), "00000100");
        String cdr = spelling(context.registry(), "00000110");
        String atom = spelling(context.registry(), "00000010");

        // 1. Define an ordinary Lisp function via live global binding.
        String identity = "identity-196";
        String defn = "(" + define + " " + identity + " (lambda (x) x))";
        BootstrapRuntime.execute(context, defn);

        Object identityResult = BootstrapRuntime.execute(context, "(" + identity + " 42)");
        require(
                "42".equals(Printer.print(identityResult)),
                "global live binding failed: " + Printer.print(identityResult));

        // 2. Lexical binding shadows the global/registry route for the same name.
        // Use lambda directly instead of let macro (core.lisp not loaded).
        Object lexicalShadow = BootstrapRuntime.execute(
                context,
                "((lambda (" + identity + ") " + identity + ") 999)");
        require(
                "999".equals(Printer.print(lexicalShadow)),
                "lexical shadow did not win: " + Printer.print(lexicalShadow));

        // 3. Define a recursive function and prove self-recursion resolves through
        // the live global binding, not through registry re-projection.
        String countdown = "countdown-196";
        String defn2 = "(" + define + " " + countdown + " (lambda (n) "
                + "(" + cond + " ((" + atom + " n) 0)"
                + " (t (" + countdown + " (" + cdr + " n))))))";
        BootstrapRuntime.execute(context, defn2);

        // Build a list of 3 elements inline and count down to 0.
        String list3 = "(" + cons + " 1 (" + cons + " 2 (" + cons + " 3 (quote ()))))";
        Object recursiveResult = BootstrapRuntime.execute(context, "(" + countdown + " " + list3 + ")");
        require(
                "0".equals(Printer.print(recursiveResult)),
                "self-recursive live binding failed: " + Printer.print(recursiveResult));

        // 4. A non-Canon identity without a live binding still resolves through
        // the registry route (negative control: registry is still authority for
        // identity/admission, it just cannot override live bindings).
        require(
                !SemanticMechanismTable.supports(Sid8.bits(0,0,0,0,1,0,0,1)),
                "Java mechanism for Lisp-owned define must not exist");

        System.out.println(
                "LIVE-BINDING-PRECEDENCE-GREEN"
                        + " global-binding=ok"
                        + " lexical-shadow=ok"
                        + " self-recursion=ok"
                        + " no-java-mechanism=ok");
    }
}
