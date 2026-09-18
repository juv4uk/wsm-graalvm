package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Monotonic Tier-1 value-fixture counter; selection is owned by ConformanceInventory. */
public final class ConformanceMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ConformanceMain <fixtures.lisp> <registry.lisp>");
            System.exit(2);
        }
        String fixturesSource = Files.readString(Path.of(args[0]));
        CanonRegistry registry = CanonRegistryLoader.load(args[1]);
        List<ConformanceInventory.Fixture> fixtures = ConformanceInventory.selectTier(fixturesSource, 1);
        int pass = 0, skippedError = 0;
        List<String> failures = new ArrayList<>();
        for (ConformanceInventory.Fixture fixture : fixtures) {
            if (fixture.error() != null) {
                skippedError++;
                report("EXPECTED-ERROR-SEPARATE-GATE", fixture.id(), fixture.expr(), fixture.error());
                continue;
            }
            Compiler compiler = new Compiler(registry);
            String actual;
            try {
                Object last = Value.NIL;
                for (WsmNode node : compiler.compileProgram(new Reader(fixture.expr()).readAll())) {
                    last = node.executeGeneric(null);
                }
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
        System.out.println("conformance tier-1 report: total=" + total + " pass=" + pass
                + " skipped-expected-error=" + skippedError + " fail=" + fail);
        for (String failure : failures) System.out.println("gate6-FAIL: " + failure);
        if (fail != 0) System.exit(1);
    }
    private static void report(String verdict, String id, String expr, String detail) {
        System.out.println("gate6[" + verdict + "] " + id + " expr=" + expr + " => " + detail);
    }
}
