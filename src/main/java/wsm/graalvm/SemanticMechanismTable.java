package wsm.graalvm;

import java.util.Map;

/**
 * Graal/JVM execution mechanisms keyed only by my-lisp exact 8-bit semantic SIDs.
 *
 * This table owns mechanism, not meaning. Source spellings are resolved before
 * this boundary and must never be accepted here.
 */
public final class SemanticMechanismTable {

    @FunctionalInterface
    private interface Mechanism {
        Object invoke(Object[] args);
    }

    private static final Map<String, Mechanism> TABLE = Map.ofEntries(
            Map.entry("00000010", SemanticMechanismTable::invoke00000010),
            Map.entry("00000011", SemanticMechanismTable::invoke00000011),
            Map.entry("00000100", SemanticMechanismTable::invoke00000100),
            Map.entry("00000101", SemanticMechanismTable::invoke00000101),
            Map.entry("00000110", SemanticMechanismTable::invoke00000110),
            Map.entry("00001101", SemanticMechanismTable::invoke00001101),
            Map.entry("00011010", SemanticMechanismTable::invoke00011010),
            Map.entry("00011011", SemanticMechanismTable::invoke00011011),
            Map.entry("00011100", SemanticMechanismTable::invoke00011100),
            Map.entry("01000011", SemanticMechanismTable::invoke01000011),
            Map.entry("01001100", SemanticMechanismTable::invoke01001100),
            Map.entry("00111010", SemanticMechanismTable::invoke00111010)
    );

    private SemanticMechanismTable() {}

    public static boolean supports(String semanticId) {
        return TABLE.containsKey(semanticId);
    }

    public static Object invoke(String semanticId, Object[] args) {
        Mechanism mechanism = TABLE.get(semanticId);
        if (mechanism == null) {
            throw new WsmError(
                    WsmError.Kind.TYPE,
                    "semantic identity is not a callable value: " + semanticId);
        }
        return mechanism.invoke(args);
    }

    private static Object invoke00000010(Object[] args) {
        WsmError.arity(args, 1, "00000010");
        return Value.structuralKind(args[0]);
    }

    private static Object invoke00000011(Object[] args) {
        WsmError.arity(args, 2, "00000011");
        return WsmNode.EqNode.eqRecord(args[0], args[1]);
    }

    private static Object invoke00000100(Object[] args) {
        WsmError.arity(args, 2, "00000100");
        return new Value.Pair(args[0], args[1]);
    }

    private static Object invoke00000101(Object[] args) {
        WsmError.arity(args, 1, "00000101");
        if (!(args[0] instanceof Value.Pair pair)) {
            throw new WsmError(WsmError.Kind.TYPE, "0005 expects a pair");
        }
        return pair.car;
    }

    private static Object invoke00000110(Object[] args) {
        WsmError.arity(args, 1, "00000110");
        if (!(args[0] instanceof Value.Pair pair)) {
            throw new WsmError(WsmError.Kind.TYPE, "0006 expects a pair");
        }
        return pair.cdr;
    }

    private static Object invoke00001101(Object[] args) {
        if (args.length == 0) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    "1001 expects at least 1 argument");
        }

        Value.NumberValue result = requireNumber("00001101", args[0]);
        if (args.length == 1) {
            return new Value.NumberValue(
                    result.numerator().negate(),
                    result.denominator());
        }

        for (int i = 1; i < args.length; i++) {
            Value.NumberValue operand = requireNumber("00001101", args[i]);
            result = subtractExact(result, operand);
        }
        return result;
    }

    private static Value.NumberValue subtractExact(
            Value.NumberValue left,
            Value.NumberValue right) {
        return new Value.NumberValue(
                left.numerator().multiply(right.denominator())
                        .subtract(right.numerator().multiply(left.denominator())),
                left.denominator().multiply(right.denominator()));
    }

    private static Object invoke00011010(Object[] args) {
        return exactQComparison("00011010", args, -1);
    }

    private static Object invoke00011011(Object[] args) {
        return exactQComparison("00011011", args, 1);
    }

    private static Object invoke00011100(Object[] args) {
        return exactQComparison("00011100", args, 0);
    }

    private static Object exactQComparison(String id, Object[] args, int relation) {
        if (args.length == 0) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    id + ": expected at least 1 argument, received 0");
        }

        Value.NumberValue previous = requireNumber(id, args[0]);
        boolean holds = true;
        for (int i = 1; i < args.length; i++) {
            Value.NumberValue current = requireNumber(id, args[i]);
            int comparison = compareExact(previous, current);
            if (Integer.signum(comparison) != relation) {
                holds = false;
            }
            previous = current;
        }

        return Value.NumberValue.integer(
                java.math.BigInteger.valueOf(holds ? 1L : 0L));
    }

    private static Value.NumberValue requireNumber(String id, Object value) {
        if (!(value instanceof Value.NumberValue number)) {
            throw new WsmError(
                    WsmError.Kind.TYPE,
                    id + " expects an exact-rational sequence");
        }
        return number;
    }

    private static int compareExact(Value.NumberValue left, Value.NumberValue right) {
        return left.numerator().multiply(right.denominator())
                .compareTo(right.numerator().multiply(left.denominator()));
    }

    private static Object invoke01000011(Object[] args) {
        WsmError.arity(args, 1, "01000011");
        if (!(args[0] instanceof Value.StringValue text)) {
            throw new WsmError(WsmError.Kind.TYPE, "1052 expects a string");
        }
        return Value.symbol(text.value);
    }

    private static Object invoke01001100(Object[] args) {
        WsmError.arity(args, 1, "01001100");
        return new Value.StringValue(CanonicalSerializer.write(args[0]));
    }

    private static Object invoke00111010(Object[] args) {
        if (args.length != 2) {
            throw new WsmError(WsmError.Kind.ARITY, "1043 expects 2 string arguments");
        }
        StringBuilder out = new StringBuilder();
        for (Object a : args) {
            if (!(a instanceof Value.StringValue text)) {
                throw new WsmError(WsmError.Kind.TYPE, "1043 expects strings");
            }
            out.append(text.value);
        }
        return new Value.StringValue(out.toString());
    }

}
