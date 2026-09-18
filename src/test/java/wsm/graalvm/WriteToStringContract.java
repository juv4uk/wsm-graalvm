package wsm.graalvm;

import java.math.BigInteger;
import java.util.List;

/** Focused executable witness for stable semantic ID 1061. */
public final class WriteToStringContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object invoke(Object value) {
        return SemanticMechanismTable.invoke(
                "1061",
                new Object[] { value });
    }

    private static Object roundTrip(String text) {
        List<Object> forms = new Reader(text).readAll();
        require(forms.size() == 1, "canonical text must read as one form");
        return ReaderDatum.toValue(forms.get(0));
    }

    private static void expectError(
            Object value,
            WsmError.Kind expected) {
        try {
            invoke(value);
            throw new AssertionError("expected " + expected);
        } catch (WsmError error) {
            require(error.kind == expected,
                    "expected " + expected + ", got " + error.contractKind());
        }
    }

    public static void main(String[] args) {
        require(invoke(Value.NIL) instanceof Value.StringValue,
                "1061 must return a language StringValue");
        require(((Value.StringValue) invoke(Value.symbol("radio"))).value.equals("radio"),
                "symbol must use readable token");

        Value.StringValue escaped = (Value.StringValue) invoke(
                new Value.StringValue("line\n\t\"\\"));
        String escapedExpected = "\"line\\n\\t\\\"\\\\\"";
        require(escaped.value.equals(escapedExpected),
                "string escaping must match canonical wire format: " + escaped.value);

        Object proper = Value.list(List.of(
                Value.symbol("alpha"),
                Value.symbol("beta"),
                Value.NumberValue.integer(BigInteger.valueOf(42))));
        String properText = ((Value.StringValue) invoke(proper)).value;
        require(properText.equals("(alpha beta 42)"),
                "proper list canonical text mismatch: " + properText);
        require(WsmNode.Structural.equals(roundTrip(properText), proper),
                "proper list canonical text must round-trip structurally");

        Value.Pair dotted = new Value.Pair(
                Value.symbol("alpha"),
                Value.symbol("omega"));
        String dottedText = ((Value.StringValue) invoke(dotted)).value;
        require(dottedText.equals("(alpha . omega)"),
                "dotted list canonical text mismatch: " + dottedText);
        require(WsmNode.Structural.equals(roundTrip(dottedText), dotted),
                "dotted list canonical text must round-trip structurally");

        Value.NumberValue rational = new Value.NumberValue(
                BigInteger.TEN,
                BigInteger.valueOf(20));
        require(((Value.StringValue) invoke(rational)).value.equals("1/2"),
                "exact rational must be reduced canonically");

        try {
            SemanticMechanismTable.invoke("1061", new Object[0]);
            throw new AssertionError("expected Arity");
        } catch (WsmError error) {
            require(error.kind == WsmError.Kind.ARITY,
                    "expected Arity, got " + error.contractKind());
        }
        expectError(new Value.SemanticRef("0010"), WsmError.Kind.TYPE);

        System.out.println("WRITE-TO-STRING-1061-CONTRACT-OK");
    }
}
