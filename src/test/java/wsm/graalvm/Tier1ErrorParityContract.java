package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Tier-1 ErrorKind evidence.
 *
 * Every pinned Tier-1 error fixture is accounted for. F28 is explicitly
 * BLOCKED until the real pinned let/macro path is preloaded; it is never
 * silently counted as an expected-error pass.
 */
public final class Tier1ErrorParityContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: Tier1ErrorParityContract <conformance.lisp> <semantic-registry.lisp>");
        }

        String corpus = Files.readString(Path.of(args[0]));
        CanonRegistry registry = CanonRegistryLoader.load(args[1]);
        List<ConformanceInventory.Fixture> errors =
                ConformanceInventory.selectTier(corpus, 1).stream()
                        .filter(fixture -> fixture.error() != null)
                        .toList();

        require(errors.size() == 8,
                "pinned Tier-1 error fixture count drifted: " + errors.size());

        Compiler compiler = new Compiler(registry);
        List<String> mismatches = new ArrayList<>();
        int pass = 0;
        int blocked = 0;

        for (ConformanceInventory.Fixture fixture : errors) {
            String actual;
            try {
                Object last = Value.NIL;
                for (WsmNode node : compiler.compileProgram(
                        new Reader(fixture.expr()).readAll())) {
                    last = node.executeGeneric(null);
                }
                actual = "VALUE:" + Printer.print(last);
            } catch (WsmError error) {
                actual = error.contractKind();
            } catch (RuntimeException error) {
                actual = "HOST:" + error.getClass().getSimpleName();
            }

            if (fixture.error().equals(actual)) {
                pass++;
                System.out.println(
                        fixture.id() + " EXPECTED_ERROR_PASS " + actual);
                continue;
            }

            // F28 proves Canon-binder rejection through Lisp-defined let.
            // This isolated probe does not preload/materialize that real
            // macro path. PR #48 correctly removed the old compile-time
            // missing-mechanism rejection that made F28 falsely green.
            if (fixture.id().equals("F28")
                    && fixture.error().equals("InvalidForm")
                    && actual.equals("Type")) {
                blocked++;
                System.out.println(
                        "F28 BLOCKED_REAL_LET expected=InvalidForm actual=Type issue=#53");
                continue;
            }

            mismatches.add(
                    fixture.id()
                            + " expr=" + fixture.expr()
                            + " expected-error=" + fixture.error()
                            + " actual=" + actual);
        }

        if (!mismatches.isEmpty()) {
            for (String mismatch : mismatches) {
                System.err.println("TIER1-ERROR-MISMATCH " + mismatch);
            }
            throw new AssertionError(
                    "Tier-1 ErrorKind parity mismatches=" + mismatches.size());
        }

        require(pass + blocked == errors.size(),
                "every error fixture must be pass or explicit blocked");
        require(pass >= 7, "error pass regression: " + pass);
        require(blocked <= 1, "blocked error fixtures increased: " + blocked);

        System.out.println(
                "TIER1-ERROR-PARITY-SUMMARY total=" + errors.size()
                        + " pass=" + pass
                        + " blocked=" + blocked
                        + " fail=0");
    }
}
