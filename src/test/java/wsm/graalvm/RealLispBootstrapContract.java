package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * End-to-end M1 bootstrap witness:
 * pinned Lisp macro library produces the MacroValue consumed by the normal
 * registry-owned 0012 peer installer before core.lisp is evaluated.
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

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: RealLispBootstrapContract <wsm-graalvm-root>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);

        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        Object canonResult = BootstrapRuntime.execute(
                context,
                Files.readString(
                        repo.resolve("external/my-lisp/lib/canon.lisp")));
        require(canonResult != null, "canon bootstrap returned no value");

        BootstrapClosureLoader.Source macroSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/macro.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/macro.lisp"));

        Object macroValue = BootstrapRuntime.execute(context, macroSource.text());
        require(
                macroValue instanceof GlobalBindings.MacroValue,
                "lib/macro.lisp must return the raw Lisp MacroValue, got: "
                        + macroValue);

        Set<String> peers = MacroPeerInstaller.install(
                context.registry(),
                context.globals(),
                (GlobalBindings.MacroValue) macroValue);
        require(!peers.isEmpty(), "registry identity 0012 has no admitted peers");

        BootstrapClosureLoader.Source coreSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/core.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/core.lisp"));

        BootstrapRuntime.execute(context, coreSource.text());

        String defmacro = spelling(context.registry(), "0012");
        String let = spelling(context.registry(), "1141");

        require(
                peers.contains(defmacro),
                "registry-selected defmacro peer was not installed: " + defmacro);

        // let is itself Lisp-defined in the pinned core library. Using it
        // here proves the MacroValue returned by lib/macro.lisp was actually
        // installed on the 0012 registry peers before core.lisp was evaluated.
        String witness =
                "(" + let + " ((cutover-value 42)) cutover-value)";

        Object result = BootstrapRuntime.execute(context, witness);
        require(
                "42".equals(String.valueOf(result)),
                "Lisp-owned let macro did not expand/evaluate on Graal: " + result);


        System.out.println(
                "REAL-LISP-BOOTSTRAP-GREEN peers=" + peers.size()
                        + " macro=" + defmacro);
    }
}
