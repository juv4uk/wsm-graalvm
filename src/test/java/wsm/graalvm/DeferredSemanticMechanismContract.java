package wsm.graalvm;

import java.util.List;

/**
 * Contract for #45: semantic identity survives compilation independently
 * of whether this substrate has already materialized its host mechanism.
 */
public final class DeferredSemanticMechanismContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object executeAll(List<WsmNode> nodes) {
        Object last = Value.NIL;
        for (WsmNode node : nodes) {
            last = node.executeGeneric(null);
        }
        return last;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: DeferredSemanticMechanismContract <semantic-registry.lisp>");
        }

        CanonRegistry registry = CanonRegistryLoader.load(args[0]);
        require("1043".equals(registry.semanticIdForToken("1043")),
                "machine ID 1043 must be admitted by the pinned registry");
        require(!SemanticMechanismTable.supports("1043"),
                "this witness requires 1043 to remain unmaterialized");

        Compiler compiler = new Compiler(registry);

        List<WsmNode> definition = compiler.compileProgram(
                new Reader("(0011 deferred (0010 (x) (1043 x x)))").readAll());
        Object defined = executeAll(definition);
        require(defined instanceof Closure,
                "definition containing unmaterialized 1043 must still produce a closure");

        List<WsmNode> dormantCall = compiler.compileProgram(
                new Reader("(1043 (0001 a) (0001 b))").readAll());
        require(dormantCall.size() == 1,
                "admitted callable semantic identity must compile before invocation");

        System.out.println(
                "DEFERRED-SEMANTIC-MECHANISM-CONTRACT-OK id=1043 mechanism=absent");
    }
}
