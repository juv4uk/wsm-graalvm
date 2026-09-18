package wsm.graalvm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Runtime value space owned by the Graal substrate mechanism. */
public final class Value {
    public static final Object NIL = new TruffleNil();

    /** Internal frame sentinel; never a Lisp-visible value. */
    public static final Object UNBOUND = new Object() {
        @Override public String toString() { return "#<unbound>"; }
    };

    public static final class TruffleNil implements com.oracle.truffle.api.interop.TruffleObject {
        private TruffleNil() {}
        @Override public String toString() { return "()"; }
    }

    public static final class Symbol implements com.oracle.truffle.api.interop.TruffleObject {
        public final String name;
        Symbol(String name) { this.name = name; }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) {
            return o instanceof Symbol s && s.name.equals(name);
        }
        @Override public int hashCode() { return name.hashCode(); }
    }

    public static final class StringValue implements com.oracle.truffle.api.interop.TruffleObject {
        public final String value;
        StringValue(String value) { this.value = value; }
        @Override public String toString() { return value; }
        @Override public boolean equals(Object o) {
            return o instanceof StringValue s && s.value.equals(value);
        }
        @Override public int hashCode() { return value.hashCode(); }
    }

    /** Exact numeric value represented as a reduced rational. */
    public record NumberValue(BigInteger numerator, BigInteger denominator)
            implements com.oracle.truffle.api.interop.TruffleObject {
        public NumberValue {
            if (denominator.signum() == 0) {
                throw new WsmError(WsmError.Kind.DIVISION_BY_ZERO, "numeric denominator is zero");
            }
            if (denominator.signum() < 0) {
                numerator = numerator.negate();
                denominator = denominator.negate();
            }
            BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }

        public static NumberValue integer(BigInteger value) {
            return new NumberValue(value, BigInteger.ONE);
        }

        public static NumberValue decimal(String token) {
            try {
                BigDecimal decimal = new BigDecimal(token);
                BigInteger unscaled = decimal.unscaledValue();
                int scale = decimal.scale();
                if (scale <= 0) {
                    return integer(unscaled.multiply(BigInteger.TEN.pow(-scale)));
                }
                return new NumberValue(unscaled, BigInteger.TEN.pow(scale));
            } catch (NumberFormatException e) {
                throw new WsmError(WsmError.Kind.PARSE, "invalid numeric literal: " + token);
            }
        }

        @Override public String toString() {
            return denominator.equals(BigInteger.ONE)
                    ? numerator.toString()
                    : numerator + "/" + denominator;
        }
    }

    public static final class Pair implements com.oracle.truffle.api.interop.TruffleObject {
        public Object car;
        public Object cdr;
        public Pair(Object car, Object cdr) { this.car = car; this.cdr = cdr; }
    }

    public record SemanticRef(String id) implements com.oracle.truffle.api.interop.TruffleObject {
        public SemanticRef {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("semantic id must be non-empty");
            }
        }
    }

    private static final java.util.Map<String, Symbol> INTERN = new java.util.HashMap<>();

    public static Symbol symbol(String name) {
        return INTERN.computeIfAbsent(name, Symbol::new);
    }

    public static Pair pair(Object car, Object cdr) {
        return new Pair(car, cdr);
    }

    public static Object list(List<Object> items) {
        Object result = NIL;
        for (int i = items.size() - 1; i >= 0; i--) {
            result = new Pair(items.get(i), result);
        }
        return result;
    }

    public static Object record(String kind, String state) {
        return list(List.of(symbol(kind), symbol(state)));
    }

    public static boolean isAtom(Object v) {
        return !(v instanceof Pair);
    }

    static final Symbol SYM_IDENTITY_RELATION = symbol("identity-relation");
    static final Symbol SYM_SAME = symbol("same");

    public static Object identitySame() {
        return record("identity-relation", "same");
    }

    public static Object structuralKind(Object v) {
        if (v == NIL) return record("structural-kind", "empty-list");
        if (v instanceof Pair) return record("structural-kind", "pair");
        return record("structural-kind", "atom");
    }
}
