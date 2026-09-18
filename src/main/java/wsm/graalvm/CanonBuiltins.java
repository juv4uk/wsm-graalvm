package wsm.graalvm;

/**
 * Canon mechanisms keyed only by numeric semantic id.
 * Ядро не знає слів — тільки IDs. Слова живуть у registry як дані.
 */
import java.util.List;

public final class CanonBuiltins {

    public static final java.util.List<String> CALLABLE =
            List.of("0002", "0003", "0004", "0005", "0006");

    private CanonBuiltins() {}

    public static WsmFunc forId(String id) {
        return switch (id) {
            case "0002" -> new WsmFunc() {
                @Override public Object call(Object[] args) {
                    WsmError.arity(args, 1, "0002");
                    return Value.structuralKind(args[0]);
                }
            };
            case "0003" -> new WsmFunc() {
                @Override public Object call(Object[] args) {
                    WsmError.arity(args, 2, "0003");
                    return wsm.graalvm.WsmNode.EqNode.eqRecord(args[0], args[1]);
                }
            };
            case "0004" -> new WsmFunc() {
                @Override public Object call(Object[] args) {
                    WsmError.arity(args, 2, "0004");
                    return new Value.Pair(args[0], args[1]);
                }
            };
            case "0005" -> new WsmFunc() {
                @Override public Object call(Object[] args) {
                    WsmError.arity(args, 1, "0005");
                    if (!(args[0] instanceof Value.Pair p))
                        throw new WsmError(WsmError.Kind.TYPE, "0005 expects a pair");
                    return p.car;
                }
            };
            case "0006" -> new WsmFunc() {
                @Override public Object call(Object[] args) {
                    WsmError.arity(args, 1, "0006");
                    if (!(args[0] instanceof Value.Pair p))
                        throw new WsmError(WsmError.Kind.TYPE, "0006 expects a pair");
                    return p.cdr;
                }
            };
            default -> throw new WsmError(WsmError.Kind.INVALID_FORM, id + " has no callable value");
        };
    }
}
