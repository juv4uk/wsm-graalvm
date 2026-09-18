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
            "0006", SemanticMechanismTable::invoke0006
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
}
