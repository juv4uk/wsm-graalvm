package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

/**
 * Focused #216 contract:
 * - self-tail LoopNode may reuse an unescaped frame;
 * - once a nested closure captures that frame, reuse must stop.
 */
public final class SelfTailLoopContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String spelling(CanonRegistry registry, String id) {
        CanonRegistry.Row row = registry.row(id);
        for (String key : new String[] {"en", "sym", "uk", "ukr", "sa"}) {
            String value = row.surfaces().get(key);
            if (value != null && !value.isBlank() && !id.equals(value) && !"—".equals(value)) {
                return value;
            }
        }
        return row.surfaces().values().stream()
                .filter(s -> s != null && !s.isBlank() && !id.equals(s) && !"—".equals(s))
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no admitted surface for " + id));
    }

    private static WsmContext bootstrap(Path repo) throws Exception {
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
        Object macroValue = BootstrapRuntime.execute(context, macroSource.text());
        require(macroValue instanceof GlobalBindings.MacroValue,
                "macro bootstrap must return MacroValue");
        Set<String> peers = MacroPeerInstaller.install(
                context.registry(),
                context.globals(),
                (GlobalBindings.MacroValue) macroValue);
        require(!peers.isEmpty(), "semantic 0012 must have peers");

        BootstrapClosureLoader.Source coreSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/core.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/core.lisp"));
        BootstrapRuntime.execute(context, coreSource.text());
        return context;
    }

    private static String captureProgram(WsmContext context) {
        CanonRegistry r = context.registry();
        String define = spelling(r, "0011");
        String lambda = spelling(r, "0010");
        String cond = spelling(r, "0007");
        String atom = spelling(r, "0002");
        String cdr = spelling(r, "0006");
        String quote = spelling(r, "0001");

        return "(" + define + " capture-loop (" + lambda + " (xs keeper) "
                + "(" + cond
                + " ((" + atom + " xs) (structural-kind empty-list) (keeper))"
                + " ((" + atom + " xs) (structural-kind pair)"
                + " (capture-loop (" + cdr + " xs) (" + lambda + " () xs)))"
                + ")))";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: SelfTailLoopContract <wsm-graalvm-root>");
        }
        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        WsmContext context = bootstrap(repo);

        BootstrapRuntime.execute(context, captureProgram(context));

        String lambda = spelling(context.registry(), "0010");
        String quote = spelling(context.registry(), "0001");
        Object captured = BootstrapRuntime.execute(
                context,
                "(capture-loop (" + quote + " (a b))"
                        + " (" + lambda + " () (" + quote + " start)))");

        require(
                "(b)".equals(Printer.print(captured)),
                "captured frame changed across tail iteration: " + Printer.print(captured));

        System.out.println("SELF-TAIL-LOOP-CAPTURE-OK result=" + Printer.print(captured));
    }
}
