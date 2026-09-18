package wsm.graalvm;

import java.util.List;

/** Executable contract for GitHub #7. */
public final class ErrorKindContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static WsmError expect(Runnable action, WsmError.Kind expected) {
        try {
            action.run();
            throw new AssertionError("expected " + expected.contractName());
        } catch (WsmError error) {
            require(error.kind == expected,
                    "expected " + expected.contractName()
                            + ", got " + error.contractKind()
                            + " (" + error.detail + ")");
            return error;
        }
    }

    public static void main(String[] args) {
        List<String> expected = List.of(
                "Parse",
                "UnknownSymbol",
                "Arity",
                "Type",
                "InvalidForm",
                "OutOfMemory",
                "NumericOverflow",
                "DivisionByZero"
        );

        require(WsmError.closedVocabulary().equals(expected),
                "closed ErrorKind vocabulary drifted");

        GlobalBindings globals = new GlobalBindings();
        WsmError unknown = expect(
                () -> globals.lookup("never-defined"),
                WsmError.Kind.UNKNOWN_SYMBOL);
        require(unknown.getMessage().startsWith("UnknownSymbol:"),
                "observable message must expose contract spelling");

        expect(
                () -> SemanticMechanismTable.invoke("0005", new Object[]{}),
                WsmError.Kind.ARITY);

        expect(
                () -> SemanticMechanismTable.invoke("0005", new Object[]{1L}),
                WsmError.Kind.TYPE);

        expect(
                () -> new CanonRegistry().row("9999"),
                WsmError.Kind.INVALID_FORM);

        expect(
                () -> WsmError.arityCheck("x".repeat(4_000_001)),
                WsmError.Kind.NUMERIC_OVERFLOW);

        // These two kinds are part of the closed language vocabulary even
        // before this M0 substrate has a resource-budget allocator or numeric
        // division mechanism that can naturally trigger them.
        require(WsmError.Kind.OUT_OF_MEMORY.contractName().equals("OutOfMemory"),
                "OutOfMemory vocabulary entry missing");
        require(WsmError.Kind.DIVISION_BY_ZERO.contractName().equals("DivisionByZero"),
                "DivisionByZero vocabulary entry missing");

        System.out.println("ERROR-KIND-CONTRACT-OK");
    }
}
