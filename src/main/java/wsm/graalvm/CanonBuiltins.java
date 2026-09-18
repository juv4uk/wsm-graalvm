package wsm.graalvm;

/**
 * Transitional compatibility bridge.
 *
 * Canonical Lisp values must move to SemanticRef(ID) (#2). Until that lands,
 * old M0 callers may still request a WsmFunc wrapper, but the semantic
 * implementation itself lives in SemanticMechanismTable and is keyed only by
 * the numeric ID.
 */
import java.util.List;

@Deprecated
public final class CanonBuiltins {

    public static final java.util.List<String> CALLABLE =
            List.of("0002", "0003", "0004", "0005", "0006");

    private CanonBuiltins() {}

    /**
     * Compatibility only: do not use this object as language identity.
     * Remove when #2 wires SemanticRef(ID) directly to SemanticMechanismTable.
     */
    public static WsmFunc forId(String id) {
        if (!SemanticMechanismTable.supports(id)) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    id + " has no callable mechanism");
        }
        return args -> SemanticMechanismTable.invoke(id, args);
    }
}
