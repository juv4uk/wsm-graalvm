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

    private static final Map<String, Mechanism> TABLE = Map.of(
            "0002", SemanticMechanismTable::invoke0002,
            "0003", SemanticMechanismTable::invoke0003,
            "0004", SemanticMechanismTable::invoke0004,
            "0005", SemanticMechanismTable::invoke0005,
            "0006", SemanticMechanismTable::invoke0006,
            "1016", SemanticMechanismTable::invoke1016,
            "1022", SemanticMechanismTable::invoke1022,
            "1052", SemanticMechanismTable::invoke1052,
            "1061", SemanticMechanismTable::invoke1061
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

    private static Object invoke1016(Object[] args) {
        WsmError.arity(args, 2, "1016");
        if (!(args[0] instanceof Value.NumberValue a)
                || !(args[1] instanceof Value.NumberValue b)) {
            throw new WsmError(WsmError.Kind.TYPE, "1016 expects two numbers");
        }
        return a.numerator().equals(b.numerator())
                && a.denominator().equals(b.denominator())
                ? Value.symbol("t")
                : Value.NIL;
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
