package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

/**
 * Evidence-only probe for #204.
 *
 * Uses an exact integer countdown through semantic 1001 so the recursion
 * measurement is not contaminated by ReaderDatum converting a huge quoted
 * list before guest execution begins.
 */
public final class DeepRecursionProbe {
    private static String spelling(CanonRegistry registry, Sid8 id) {
        CanonRegistry.Row row = registry.row(id);
        for (String key : new String[] {"en", "sym", "uk", "ukr", "sa"}) {
            String value = row.surfaces().get(key);
            if (value != null && !value.isBlank() && !id.equals(value) && !"—".equals(value)) {
                return value;
            }
        }
        return row.surfaces().values().stream()
                .filter(s -> s != null && !s.isBlank() && !id.matchesBareToken(s) && !"—".equals(s))
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
        if (!(macroValue instanceof GlobalBindings.MacroValue macro)) {
            throw new AssertionError("lib/macro.lisp did not produce MacroValue");
        }
        Set<String> peers = MacroPeerInstaller.install(context.registry(), context.globals(), macro);
        if (peers.isEmpty()) {
            throw new AssertionError("semantic 0012 has no admitted macro peers");
        }

        BootstrapClosureLoader.Source coreSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/core.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/core.lisp"));
        BootstrapRuntime.execute(context, coreSource.text());
        return context;
    }

    private static String definition(WsmContext context, String mode) {
        CanonRegistry r = context.registry();
        String define = spelling(r, Sid8.bits(0,0,0,0,1,0,0,1));
        String lambda = spelling(r, Sid8.bits(0,0,0,0,1,0,0,0));
        String cond = spelling(r, Sid8.bits(0,0,0,0,0,1,1,1));
        String eq = spelling(r, Sid8.bits(0,0,0,0,0,0,1,1));
        String cons = spelling(r, Sid8.bits(0,0,0,0,0,1,0,0));
        String quote = spelling(r, Sid8.bits(0,0,0,0,0,0,0,1));
        String subtract = spelling(r, Sid8.bits(0,0,0,0,1,1,0,1));

        String recursive = "tail".equals(mode)
                ? "(deep-loop (" + subtract + " n 1))"
                : "(" + cons + " (" + quote + " probe)"
                        + " (deep-loop (" + subtract + " n 1)))";

        return "(" + define + " deep-loop (" + lambda + " (n) "
                + "(" + cond
                + " ((" + eq + " n 0) (identity-relation same) (" + quote + " ()))"
                + " ((" + eq + " n 0) (identity-relation distinct) " + recursive + ")"
                + ")))";
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: DeepRecursionProbe <wsm-graalvm-root> <tail|non-tail> <depth>");
        }
        String mode = args[1];
        if (!"tail".equals(mode) && !"non-tail".equals(mode)) {
            throw new IllegalArgumentException("mode must be tail or non-tail");
        }
        int depth = Integer.parseInt(args[2]);
        if (depth < 0) throw new IllegalArgumentException("depth must be non-negative");

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        WsmContext context = bootstrap(repo);
        BootstrapRuntime.execute(context, definition(context, mode));

        try {
            Object value = BootstrapRuntime.execute(context, "(deep-loop " + depth + ")");
            System.out.println(
                    "DEEP-RECURSION-OBSERVED mode=" + mode
                            + " depth=" + depth
                            + " outcome=value"
                            + " value-kind=" + Printer.print(Value.structuralKind(value)));
        } catch (StackOverflowError overflow) {
            System.out.println(
                    "DEEP-RECURSION-OBSERVED mode=" + mode
                            + " depth=" + depth
                            + " outcome=host-stack-overflow");
        } catch (WsmError error) {
            System.out.println(
                    "DEEP-RECURSION-OBSERVED mode=" + mode
                            + " depth=" + depth
                            + " outcome=lisp-error"
                            + " kind=" + error.contractKind());
        }
    }
}
