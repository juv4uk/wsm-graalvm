package wsm.graalvm;

import java.util.Map;

/** Graal execution mechanisms keyed only by numeric semantic IDs. */
public final class SemanticMechanismTable {
    @FunctionalInterface
    private interface Mechanism { Object invoke(Object[] args); }

    private static final Map<String, Mechanism> TABLE = Map.of(
            "0002", SemanticMechanismTable::invoke0002,
            "0003", SemanticMechanismTable::invoke0003,
            "0004", SemanticMechanismTable::invoke0004,
            "0005", SemanticMechanismTable::invoke0005,
            "0006", SemanticMechanismTable::invoke0006);

    private SemanticMechanismTable() {}

    public static boolean supports(String id) { return TABLE.containsKey(id); }

    public static Object invoke(String id, Object[] args) {
        Mechanism m = TABLE.get(id);
        if (m == null) {
            throw new WsmError(WsmError.Kind.TYPE, "semantic identity is not callable: " + id);
        }
        return m.invoke(args);
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
        if (!(args[0] instanceof Value.Pair p))
            throw new WsmError(WsmError.Kind.TYPE, "0005 expects a pair");
        return p.car;
    }

    private static Object invoke0006(Object[] args) {
        WsmError.arity(args, 1, "0006");
        if (!(args[0] instanceof Value.Pair p))
            throw new WsmError(WsmError.Kind.TYPE, "0006 expects a pair");
        return p.cdr;
    }
}
