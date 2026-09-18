package wsm.graalvm;

import java.util.List;

/** RED/green contract for #32: raw Tier-1 corpus accounting, not semantic selection policy. */
public final class ConformanceInventoryContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void expectInvalid(String source) {
        try {
            ConformanceInventory.selectTier(source, 1);
            throw new AssertionError("expected malformed fixture to fail closed");
        } catch (WsmError error) {
            require(error.kind == WsmError.Kind.INVALID_FORM,
                    "malformed fixture must be InvalidForm, got " + error.contractKind());
        }
    }

    public static void main(String[] args) {
        String source = """
            ((expr . "(quote radio)") (expected . "radio") (tier . 1)
             (role . "constitutive") (requires . (immutable-canon))
             (since-contract . (6 0)))
            ((expr . "(print 3)") (expected . "3") (tier . 2))
            ((expr . "(undefined-symbol)") (error . "UnknownSymbol") (tier . 1)
             (axioms . (S2)))
            """;

        List<ConformanceInventory.Fixture> fixtures =
                ConformanceInventory.selectTier(source, 1);

        require(fixtures.size() == 2, "expected exactly two Tier-1 fixtures");

        ConformanceInventory.Fixture f1 = fixtures.get(0);
        require(f1.id().equals("F01"), "first selected fixture must be F01");
        require(f1.expr().equals("(quote radio)"), "F01 expr mismatch");
        require(f1.expected().equals("radio"), "F01 expected mismatch");
        require(f1.error() == null, "F01 must be a success fixture");
        require("constitutive".equals(f1.role()), "F01 role mismatch");
        require(f1.requires().equals(List.of("immutable-canon")), "F01 requires mismatch");
        require(f1.sinceContract().equals(List.of(6L, 0L)), "F01 contract version mismatch");

        ConformanceInventory.Fixture f2 = fixtures.get(1);
        require(f2.id().equals("F02"), "second selected fixture must be F02");
        require(f2.expr().equals("(undefined-symbol)"), "F02 expr mismatch");
        require(f2.expected() == null, "F02 must not have expected value");
        require(f2.error().equals("UnknownSymbol"), "F02 error mismatch");
        require(f2.role() == null, "absent role must remain absent");
        require(f2.requires().isEmpty(), "absent requires must be empty");
        require(f2.sinceContract().isEmpty(), "absent since-contract must be empty");

        expectInvalid("((expected . \"x\") (tier . 1))");
        expectInvalid("((expr . \"x\") (expected . \"x\") (error . \"Type\") (tier . 1))");
        expectInvalid("((expr . \"x\") (tier . 1))");

        System.out.println("CONFORMANCE-INVENTORY-CONTRACT-OK selected=" + fixtures.size());
    }
}
