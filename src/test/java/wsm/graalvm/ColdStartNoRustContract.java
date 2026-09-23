package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

/**
 * #95 cold-start witness.
 *
 * Proves that the pinned Lisp authority can bootstrap and execute on GraalVM
 * with no Rust runtime/evaluator available at execution time.
 */
public final class ColdStartNoRustContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String spelling(CanonRegistry registry, Sid8 id) {
        return registry.row(id).surfaces().values().stream()
                .filter(s -> s != null && !s.isBlank() && !id.matchesBareToken(s))
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no admitted surface for " + id));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: ColdStartNoRustContract <wsm-root> <wsm-commit> <my-lisp-pin>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        String wsmCommit = args[1];
        String myLispPin = args[2];

        require(wsmCommit.matches("[0-9a-f]{40}"), "WSM commit must be exact SHA");
        require(myLispPin.matches("[0-9a-f]{40}"), "my-lisp pin must be exact SHA");

        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);

        // Negative owner-selection witness before Lisp bootstrap:
        // abs / SID 00010000 is Lisp-owned and must have no Java mechanism.
        require(
                !SemanticMechanismTable.supports(Sid8.bits(0,0,0,1,0,0,0,0)),
                "Lisp-owned abs unexpectedly has a Java mechanism");

        WsmContext unbootstrapped = new WsmContext(closure.registryPath().toString());
        unbootstrapped.initialize();

        String missingOwnerFailure = "none";
        try {
            BootstrapRuntime.execute(unbootstrapped, "(00010000 -5)");
        } catch (WsmError error) {
            missingOwnerFailure = error.contractKind();
        }
        require(
                "Type".equals(missingOwnerFailure),
                "missing Lisp owner must fail closed as Type, got " + missingOwnerFailure);

        // Positive cold start: consume only the pinned Lisp closure.
        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        Object canonResult = BootstrapRuntime.execute(
                context,
                Files.readString(repo.resolve("external/my-lisp/lib/canon.lisp")));
        require(canonResult != null, "canon bootstrap returned no value");

        BootstrapClosureLoader.Source macroSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/macro.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/macro.lisp"));

        Object macroValue = BootstrapRuntime.execute(context, macroSource.text());
        require(
                macroValue instanceof GlobalBindings.MacroValue,
                "lib/macro.lisp must return MacroValue, got " + macroValue);

        Set<String> macroPeers = MacroPeerInstaller.install(
                context.registry(),
                context.globals(),
                (GlobalBindings.MacroValue) macroValue);
        require(!macroPeers.isEmpty(), "defmacro has no admitted peer surfaces");

        BootstrapClosureLoader.Source coreSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/core.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/core.lisp"));

        BootstrapRuntime.execute(context, coreSource.text());

        // The same identity that failed before bootstrap must now execute
        // through the live Lisp binding, still with no Java abs mechanism.
        require(
                !SemanticMechanismTable.supports(Sid8.bits(0,0,0,1,0,0,0,0)),
                "cold start must not manufacture a Java abs mechanism");

        String abs = spelling(context.registry(), Sid8.bits(0,0,0,1,0,0,0,0));
        require(
                "00010000".equals(context.registry().semanticIdForToken(abs)),
                "abs surface must resolve to exact SID 00010000");

        Object absValue = BootstrapRuntime.execute(context, "(" + abs + " -5)");
        require(
                "5".equals(Printer.print(absValue)),
                "Lisp-owned abs failed after cold start: " + Printer.print(absValue));

        // Existing higher-level Lisp-owned witness: equal? also has no Java
        // mechanism but must work after core bootstrap.
        require(
                !SemanticMechanismTable.supports(Sid8.bits(0,0,1,0,0,0,1,0)),
                "Lisp-owned equal? unexpectedly has a Java mechanism");

        String equal = spelling(context.registry(), Sid8.bits(0,0,1,0,0,0,1,0));
        Object equalValue = BootstrapRuntime.execute(
                context,
                "(" + equal + " (quote (1 2)) (quote (1 2)))");
        require(
                "(structural-relation same)".equals(Printer.print(equalValue)),
                "Lisp-owned equal? failed after cold start: " + Printer.print(equalValue));

        System.out.println(
                "COLD-START-NO-RUST-GREEN"
                        + " wsm=" + wsmCommit
                        + " my-lisp=" + myLispPin
                        + " missing-owner=" + missingOwnerFailure
                        + " abs=" + Printer.print(absValue)
                        + " equal=" + Printer.print(equalValue)
                        + " macro-peers=" + macroPeers.size());
    }
}
