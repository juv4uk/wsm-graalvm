package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Monotonic Tier-1 value-fixture counter.
 *
 * Fixture meaning and bootstrap source remain upstream-owned. Tier-1 executes
 * in one shared Graal session after the same real canon -> macro -> core
 * bootstrap used by the substrate cutover witness.
 */
public final class ConformanceMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println(
                    "usage: ConformanceMain <fixtures.lisp> <registry.lisp> <wsm-graalvm-root>");
            System.exit(2);
        }

        Path repo = Path.of(args[2]).toAbsolutePath().normalize();
        String fixturesSource = Files.readString(Path.of(args[0]));
        Path transitionPath =
                repo.resolve("external/my-lisp/tests/fixtures/conformance-transition-witness.lisp");
        String transitionSource = Files.readString(transitionPath);
        List<ConformanceInventory.Fixture> fixtures =
                ConformanceInventory.selectTierCurrent(fixturesSource, transitionSource, 1);

        WsmContext context = bootstrap(repo, args[1]);

        int pass = 0, skippedError = 0;
        List<String> failures = new ArrayList<>();
        for (ConformanceInventory.Fixture fixture : fixtures) {
            if (fixture.error() != null) {
                skippedError++;
                report(
                        "EXPECTED-ERROR-SEPARATE-GATE",
                        fixture.id(),
                        fixture.expr(),
                        fixture.error());
                continue;
            }

            String actual;
            try {
                Object last = BootstrapRuntime.execute(context, fixture.expr());
                actual = Printer.print(last);
            } catch (WsmError error) {
                actual = "throw:" + error.contractKind();
            } catch (RuntimeException host) {
                actual = "host:" + host.getClass().getSimpleName();
            }

            if (fixture.expected().equals(actual)) {
                pass++;
                report("PASS", fixture.id(), fixture.expr(), actual);
            } else {
                String detail = "expected=" + fixture.expected() + " actual=" + actual;
                report("FAIL", fixture.id(), fixture.expr(), detail);
                failures.add(fixture.id() + " " + detail);
            }
        }

        int total = fixtures.size();
        int fail = total - pass - skippedError;
        System.out.println(
                "conformance tier-1 report: total="
                        + total
                        + " pass="
                        + pass
                        + " skipped-expected-error="
                        + skippedError
                        + " fail="
                        + fail);
        for (String failure : failures) {
            System.out.println("gate6-FAIL: " + failure);
        }
        if (fail != 0) System.exit(1);
    }

    private static WsmContext bootstrap(Path repo, String registryPath) throws Exception {
        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);
        Path suppliedRegistry = Path.of(registryPath).toAbsolutePath().normalize();
        Path manifestRegistry = closure.registryPath().toAbsolutePath().normalize();
        if (!suppliedRegistry.equals(manifestRegistry)) {
            throw new IllegalArgumentException(
                    "Tier-1 registry must be the manifest-owned pinned registry: "
                            + suppliedRegistry
                            + " != "
                            + manifestRegistry);
        }

        WsmContext context = new WsmContext(manifestRegistry.toString());
        context.initialize();

        BootstrapRuntime.execute(
                context,
                Files.readString(repo.resolve("external/my-lisp/lib/canon.lisp")));

        BootstrapClosureLoader.Source macroSource =
                closure.executableSources().stream()
                        .filter(source -> source.path().endsWith("lib/macro.lisp"))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "manifest omitted lib/macro.lisp"));
        Object macroValue = BootstrapRuntime.execute(context, macroSource.text());
        if (!(macroValue instanceof GlobalBindings.MacroValue macro)) {
            throw new IllegalStateException(
                    "lib/macro.lisp did not produce MacroValue: " + macroValue);
        }

        Set<String> peers =
                MacroPeerInstaller.install(context.registry(), context.globals(), macro);
        if (peers.isEmpty()) {
            throw new IllegalStateException("registry identity 0012 has no admitted peers");
        }

        BootstrapClosureLoader.Source coreSource =
                closure.executableSources().stream()
                        .filter(source -> source.path().endsWith("lib/core.lisp"))
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "manifest omitted lib/core.lisp"));
        BootstrapRuntime.execute(context, coreSource.text());

        return context;
    }

    private static void report(String verdict, String id, String expr, String detail) {
        System.out.println(
                "gate6[" + verdict + "] " + id + " expr=" + expr + " => " + detail);
    }
}
