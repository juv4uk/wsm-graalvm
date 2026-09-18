package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Raw Tier-1 ErrorKind parity probe for #7.
 *
 * This deliberately selects every Tier-1 error fixture from the pinned corpus
 * instead of maintaining a Java fixture list.
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
        List<ConformanceInventory.Fixture> tier1 =
                ConformanceInventory.selectTier(corpus, 1);

        List<ConformanceInventory.Fixture> errors = tier1.stream()
                .filter(f -> f.error() != null)
                .toList();

        require(errors.size() == 8,
                "pinned Tier-1 error fixture count drifted: " + errors.size());

        Compiler compiler = new Compiler(registry);
        List<String> mismatches = new ArrayList<>();

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

            if (!fixture.error().equals(actual)) {
                mismatches.add(
                        fixture.id()
                                + " expr=" + fixture.expr()
                                + " expected-error=" + fixture.error()
                                + " actual=" + actual);
            } else {
                System.out.println(
                        fixture.id() + " EXPECTED_ERROR_PASS " + actual);
            }
        }

        if (!mismatches.isEmpty()) {
            for (String mismatch : mismatches) {
                System.err.println("TIER1-ERROR-MISMATCH " + mismatch);
            }
            throw new AssertionError(
                    "Tier-1 ErrorKind parity mismatches=" + mismatches.size());
        }

        System.out.println(
                "TIER1-ERROR-PARITY-OK selected=" + errors.size());
    }
}
