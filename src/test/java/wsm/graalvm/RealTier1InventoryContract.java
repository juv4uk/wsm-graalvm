package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Real pinned-corpus gate for #32. */
public final class RealTier1InventoryContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: RealTier1InventoryContract <conformance.lisp>");
        }

        String source = Files.readString(Path.of(args[0]));
        List<ConformanceInventory.Fixture> fixtures =
                ConformanceInventory.selectTier(source, 1);

        require(fixtures.size() == 36,
                "pinned raw Tier-1 count drifted: " + fixtures.size());
        require(fixtures.get(0).id().equals("F01"), "first ID must be F01");
        require(fixtures.get(0).expr().equals("(quote radio)"),
                "F01 must follow pinned file order");
        require(fixtures.get(34).id().equals("F35"), "F35 ID must be stable");
        require(fixtures.get(34).expr().equals(
                        "(cond (0 (quote zero-is-truthy)) (t (quote wrong)))"),
                "F35 raw historical fixture mismatch");
        require(fixtures.get(35).id().equals("F36"), "last ID must be F36");
        require(fixtures.get(35).expr().equals(
                        "(cond ((quote radio) antenna (quote wrong)))"),
                "F36 raw historical fixture mismatch");
        require("UnsatisfiedConditional".equals(fixtures.get(35).error()),
                "F36 must fail closed with UnsatisfiedConditional");

        String emitted = ConformanceInventory.emitLisp(fixtures, 1);
        List<Object> emittedForms = new Reader(emitted).readAll();

        require(emittedForms.size() == 37,
                "36 fixture records + 1 summary must be machine-readable");
        require(emitted.contains("((id . F01)"),
                "machine-readable output must expose stable Fxx IDs");
        require(emitted.contains("(selected . 36)"),
                "machine-readable output must expose selected count");

        System.out.print(emitted);
        System.out.println("REAL-TIER1-INVENTORY-CONTRACT-OK selected=36");
    }
}
