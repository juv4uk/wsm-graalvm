package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

/**
 * End-to-end M1 witness:
 * pinned Lisp macro source produces the MacroValue consumed by the
 * registry-owned 0012 peer installer before pinned core.lisp executes.
 */
public final class RealLispBootstrapContract {
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

    private static String surface(CanonRegistry registry, String id, String marker) {
        String spelling = registry.row(id).surfaces().get(marker);
        if (spelling == null || spelling.isBlank()) {
            throw new AssertionError("no admitted " + marker + " surface for " + id);
        }
        return spelling;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: RealLispBootstrapContract <wsm-graalvm-root>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);

        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        Object canonResult = BootstrapRuntime.executeAuthoritySource(
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
                "lib/macro.lisp must return the raw Lisp MacroValue, got: " + macroValue);

        Set<String> peers = MacroPeerInstaller.install(
                context.registry(),
                context.globals(),
                (GlobalBindings.MacroValue) macroValue);
        require(!peers.isEmpty(), "registry identity 0012 has no admitted peers");

        BootstrapClosureLoader.Source coreSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/core.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/core.lisp"));

        BootstrapRuntime.executeAuthoritySource(context, coreSource.text());

        String defmacro = spelling(context.registry(), "0012");
        String let = spelling(context.registry(), "1141");
        String equal = surface(context.registry(), "1022", "en");
        String ukEqual = surface(context.registry(), "1022", "uk");
        require(
                peers.contains(defmacro),
                "registry-selected defmacro peer was not installed: " + defmacro);

        // Post-retirement witness: the Java 1022 mechanism must be absent,
        // while the pinned Lisp-owned equal? definition remains executable.
        // Keep this on the real bootstrap path so host fallback cannot return silently.
        require(
                !SemanticMechanismTable.supports("1022"),
                "Java 1022 mechanism must remain retired");
        Object equalSame = BootstrapRuntime.execute(
                context,
                "(" + equal + " (quote (1 2)) (quote (1 2)))");
        Object equalDistinct = BootstrapRuntime.execute(
                context,
                "(" + equal + " (quote (1 2)) (quote (1 3)))");
        require(
                "(structural-relation same)".equals(Printer.print(equalSame)),
                "Lisp-owned equal? same witness failed: " + Printer.print(equalSame));
        require(
                "(structural-relation distinct)".equals(Printer.print(equalDistinct)),
                "Lisp-owned equal? distinct witness failed: " + Printer.print(equalDistinct));

        // #178: registry peers for a Lisp-owned function must point to the
        // exact same live Closure object after bootstrap materialization.
        require(
                context.globals().lookup(equal) == context.globals().lookup(ukEqual),
                "EN/UK 1022 peers are not the exact same live Lisp value");
        Object ukEqualSame = BootstrapRuntime.execute(
                context,
                "(" + ukEqual + " (quote (1 2)) (quote (1 2)))");
        require(
                "(structural-relation same)".equals(Printer.print(ukEqualSame)),
                "Lisp-owned UK equal? witness failed: " + Printer.print(ukEqualSame));

        // Snapshot law: later ordinary shadowing of one surface must not
        // retarget its already-materialized peer.
        Object sharedEqual = context.globals().lookup(equal);
        context.globals().define(ukEqual, Value.symbol("later-uk-shadow"));
        require(
                context.globals().lookup(equal) == sharedEqual,
                "later UK shadow retargeted EN 1022 peer");

        // let is Lisp-defined in pinned core.lisp. Running it here proves
        // the MacroValue returned by lib/macro.lisp was installed on the
        // 0012 peers before core.lisp was evaluated.
        Object result = BootstrapRuntime.execute(
                context,
                "(" + let + " ((cutover-value 42)) cutover-value)");
        require(
                "42".equals(String.valueOf(result)),
                "Lisp-owned let macro did not expand/evaluate on Graal: " + result);

        System.out.println(
                "REAL-LISP-BOOTSTRAP-GREEN peers=" + peers.size()
                        + " macro=" + defmacro
                        + " let=" + let
                        + " equal=" + equal
                        + " uk-equal=" + ukEqual);
    }
}
