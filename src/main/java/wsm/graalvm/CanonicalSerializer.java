package wsm.graalvm;

/**
 * Implementation of my-lisp's canonical data wire format.
 *
 * This serializer is deliberately separate from Printer: diagnostic rendering
 * may evolve freely, while semantic ID 1061 requires a stable, readable,
 * round-trippable representation for the language's serializable domain.
 */
final class CanonicalSerializer {
    private CanonicalSerializer() {}

    static String write(Object value) {
        if (value == Value.NIL) return "()";
        if (value instanceof Value.Symbol symbol) return symbol.name;
        if (value instanceof Value.StringValue string) {
            return '"' + escape(string.value) + '"';
        }
        if (value instanceof Value.NumberValue number) {
            if (number.denominator().equals(java.math.BigInteger.ONE)) {
                return number.numerator().toString();
            }
            return number.numerator() + "/" + number.denominator();
        }
        if (value instanceof Value.Pair pair) {
            return writePair(pair);
        }
        throw new WsmError(
                WsmError.Kind.TYPE,
                "1061 cannot serialize live/non-data value: "
                        + value.getClass().getSimpleName());
    }

    private static String writePair(Value.Pair pair) {
        StringBuilder out = new StringBuilder("(");
        out.append(write(pair.car));
        Object rest = pair.cdr;
        while (rest instanceof Value.Pair next) {
            out.append(' ').append(write(next.car));
            rest = next.cdr;
        }
        if (rest != Value.NIL) {
            out.append(" . ").append(write(rest));
        }
        return out.append(')').toString();
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(value.charAt(i));
            }
        }
        return out.toString();
    }
}
