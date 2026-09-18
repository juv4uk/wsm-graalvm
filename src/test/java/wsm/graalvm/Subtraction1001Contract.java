package wsm.graalvm;

import java.math.BigInteger;

/**
 * RED/green witness для semantic ID 1001.
 *
 * Значення операції належить pinned my-lisp; Java тут перевіряє лише
 * exact-rational substrate mechanism без spelling-based dispatch.
 */
public final class Subtraction1001Contract {
    private static Value.NumberValue n(long value) {
        return Value.NumberValue.integer(BigInteger.valueOf(value));
    }

    private static Value.NumberValue q(long numerator, long denominator) {
        return new Value.NumberValue(
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void expectKind(WsmError.Kind kind, Runnable thunk, String message) {
        try {
            thunk.run();
            throw new AssertionError(message + ": expected " + kind);
        } catch (WsmError error) {
            require(error.kind == kind,
                    message + ": expected " + kind + ", got " + error.kind);
        }
    }

    public static void main(String[] args) {
        require(SemanticMechanismTable.supports("1001"),
                "semantic 1001 must have a substrate mechanism");

        Object unary = SemanticMechanismTable.invoke("1001", new Object[] { n(5) });
        require(unary.equals(n(-5)),
                "unary 1001 must negate an exact integer");

        Object folded = SemanticMechanismTable.invoke(
                "1001",
                new Object[] { n(10), n(3), n(2) });
        require(folded.equals(n(5)),
                "1001 must subtract remaining operands left-to-right");

        Object rational = SemanticMechanismTable.invoke(
                "1001",
                new Object[] { q(3, 2), q(1, 2) });
        require(rational.equals(n(1)),
                "1001 must preserve exact rational arithmetic");

        expectKind(
                WsmError.Kind.ARITY,
                () -> SemanticMechanismTable.invoke("1001", new Object[] {}),
                "zero-argument 1001");
        expectKind(
                WsmError.Kind.TYPE,
                () -> SemanticMechanismTable.invoke(
                        "1001",
                        new Object[] { Value.symbol("radio") }),
                "non-number 1001");

        System.out.println("SUBTRACTION-1001-CONTRACT-OK");
    }
}
