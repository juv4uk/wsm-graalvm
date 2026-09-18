package wsm.graalvm;

import java.util.Map;

/**
 * Graal/JVM execution mechanisms keyed only by my-lisp numeric semantic IDs.
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
            Map.entry("0002", SemanticMechanismTable::invoke0002),
            Map.entry("0003", SemanticMechanismTable::invoke0003),
            Map.entry("0004", SemanticMechanismTable::invoke0004),
            Map.entry("0005", SemanticMechanismTable::invoke0005),
            Map.entry("0006", SemanticMechanismTable::invoke0006),
            Map.entry("1014", SemanticMechanismTable::invoke1014),
            Map.entry("1015", SemanticMechanismTable::invoke1015),
            Map.entry("1016", SemanticMechanismTable::invoke1016),
            Map.entry("1022", SemanticMechanismTable::invoke1022),
            Map.entry("1052", SemanticMechanismTable::invoke1052),
            Map.entry("1061", SemanticMechanismTable::invoke1061)
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

    private static Object invoke0002(Object[] args) {
        WsmError.arity(args, 1, "0002");
        return Value.structuralKind(args[0]);
    }

    private static Object invoke0003(Object[] args) {
        WsmError.arity(args, 2, "0003");
        return WsmNode.EqNode.eqRecord(args[0], args[1]);
    }

    private static Object invoke0004(Object[] args) {
        WsmError.arity(args, 2, "0004");
        return new Value.Pair(args[0], args[1]);
    }

    private static Object invoke0005(Object[] args) {
        WsmError.arity(args, 1, "0005");
        if (!(args[0] instanceof Value.Pair pair)) {
            throw new WsmError(WsmError.Kind.TYPE, "0005 expects a pair");
        }
        return pair.car;
    }

    private static Object invoke0006(Object[] args) {
        WsmError.arity(args, 1, "0006");
        if (!(args[0] instanceof Value.Pair pair)) {
            throw new WsmError(WsmError.Kind.TYPE, "0006 expects a pair");
        }
        return pair.cdr;
    }

    private static Object invoke1014(Object[] args) {
        return exactQComparison("1014", args, -1);
    }

    private static Object invoke1015(Object[] args) {
        return exactQComparison("1015", args, 1);
    }

    private static Object invoke1016(Object[] args) {
        return exactQComparison("1016", args, 0);
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

    private static Object invoke1052(Object[] args) {
        WsmError.arity(args, 1, "1052");
        if (!(args[0] instanceof Value.StringValue text)) {
            throw new WsmError(WsmError.Kind.TYPE, "1052 expects a string");
        }
        return Value.symbol(text.value);
    }

    private static Object invoke1061(Object[] args) {
        WsmError.arity(args, 1, "1061");
        return new Value.StringValue(CanonicalSerializer.write(args[0]));
    }

    private static Object invoke1022(Object[] args) {
        WsmError.arity(args, 2, "1022");
        return WsmNode.Structural.equals(args[0], args[1])
                ? Value.record("structural-relation", "same")
                : Value.record("structural-relation", "distinct");
    }
}
