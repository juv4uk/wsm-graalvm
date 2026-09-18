package wsm.graalvm;

/**
 * Focused mechanism witness for stable semantic ID 1043.
 *
 * This test deliberately invokes the numeric-ID mechanism boundary directly:
 * source-level decimal 1043 is a numeric literal and must not be repurposed as
 * syntax merely for this test. The real core bootstrap separately proves
 * surface -> registry -> semantic ID -> mechanism routing.
 */
public final class StringAppendContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void expectError(Object[] args, WsmError.Kind expected) {
        try {
            SemanticMechanismTable.invoke("1043", args);
            throw new AssertionError("expected " + expected);
        } catch (WsmError error) {
            require(error.kind == expected,
                    "expected " + expected + ", got " + error.contractKind());
        }
    }

    public static void main(String[] args) {
        Object result = SemanticMechanismTable.invoke(
                "1043",
                new Object[] {
                        new Value.StringValue("hello "),
                        new Value.StringValue("світе")
                });

        require(result instanceof Value.StringValue,
                "1043 must produce a language StringValue");
        require(((Value.StringValue) result).value.equals("hello світе"),
                "1043 must concatenate exact Unicode string contents");

        expectError(
                new Object[] {new Value.StringValue("only-one")},
                WsmError.Kind.ARITY);
        expectError(
                new Object[] {new Value.StringValue("ok"), Value.NumberValue.integer(java.math.BigInteger.valueOf(42))},
                WsmError.Kind.TYPE);

        System.out.println("STRING-APPEND-1043-CONTRACT-OK");
    }
}
