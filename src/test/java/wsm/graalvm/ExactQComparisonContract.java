package wsm.graalvm;

import java.math.BigInteger;

/** Contract for pinned exact-Q comparison mechanisms 1014/1015/1016. */
public final class ExactQComparisonContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Value.NumberValue n(long value) {
        return Value.NumberValue.integer(BigInteger.valueOf(value));
    }

    private static Value.NumberValue q(long numerator, long denominator) {
        return new Value.NumberValue(
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator));
    }

    private static long decision(String id, Object... args) {
        Object value = SemanticMechanismTable.invoke(id, args);
        require(value instanceof Value.NumberValue,
                id + " must return exact NumberValue, got: " + value);
        Value.NumberValue number = (Value.NumberValue) value;
        require(number.denominator().equals(BigInteger.ONE),
                id + " decision denominator must be 1");
        return number.numerator().longValueExact();
    }

    private static void expectKind(String id, WsmError.Kind kind, Object... args) {
        try {
            SemanticMechanismTable.invoke(id, args);
            throw new AssertionError(id + " must fail with " + kind);
        } catch (WsmError error) {
            require(error.kind == kind,
                    id + " expected " + kind + ", got " + error.kind);
        }
    }

    public static void main(String[] args) {
        require(decision("00011010", n(1), n(2), n(3)) == 1, "1014 chained true");
        require(decision("00011010", n(1), n(3), n(2)) == 0, "1014 chained false");
        require(decision("00011011", n(3), n(2), n(1)) == 1, "1015 chained true");
        require(decision("00011011", n(3), n(1), n(2)) == 0, "1015 chained false");
        require(decision("00011100", q(2, 4), q(1, 2), q(3, 6)) == 1,
                "1016 exact rational equality");
        require(decision("00011100", n(1), n(1), n(2)) == 0,
                "1016 chained false");

        for (String id : new String[] {"00011010", "00011011", "00011100"}) {
            require(decision(id, q(7, 9)) == 1,
                    id + " one-argument comparison must be true");
            expectKind(id, WsmError.Kind.ARITY);
            expectKind(id, WsmError.Kind.TYPE, n(1), Value.symbol("not-a-number"));
        }

        require(!SemanticMechanismTable.supports("00011101"),
                "derived <= must remain Lisp-owned");
        require(!SemanticMechanismTable.supports("00011110"),
                "derived >= must remain Lisp-owned");

        System.out.println("EXACT-Q-COMPARISON-CONTRACT-OK");
    }
}
