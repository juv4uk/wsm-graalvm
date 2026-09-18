package wsm.graalvm;

import java.math.BigInteger;
import org.graalvm.polyglot.Context;

/**
 * Focused witness for the migration-only two-part cond bridge.
 *
 * The expected mapping is copied from pinned my-lisp's migration-only
 * compatibility path; this test deliberately does not define a new truth
 * model for canonical three-part cond.
 */
public final class MigrationCondTruthinessContract {
    private static final Object SELECTED = Value.symbol("selected");

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Value.NumberValue exact(long value) {
        return Value.NumberValue.integer(BigInteger.valueOf(value));
    }

    private static boolean twoPartSelects(Object testValue) {
        WsmNode.CondNode node =
                new WsmNode.CondNode(
                        new WsmNode[] {new WsmNode.ConstantNode(testValue)},
                        new WsmNode[] {new WsmNode.ConstantNode(Value.NIL)},
                        new WsmNode[] {new WsmNode.ConstantNode(SELECTED)},
                        new boolean[] {true});
        return node.executeGeneric(null) == SELECTED;
    }

    private static boolean threePartSelects(Object actual, Object expected) {
        WsmNode.CondNode node =
                new WsmNode.CondNode(
                        new WsmNode[] {new WsmNode.ConstantNode(actual)},
                        new WsmNode[] {new WsmNode.ConstantNode(expected)},
                        new WsmNode[] {new WsmNode.ConstantNode(SELECTED)},
                        new boolean[] {false});
        return node.executeGeneric(null) == SELECTED;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: MigrationCondTruthinessContract <semantic-registry.lisp>");
        }

        require(twoPartSelects(Value.record("structural-kind", "empty-list")),
                "two-part cond: empty-list record must preserve historical truth");
        require(twoPartSelects(Value.record("structural-kind", "atom")),
                "two-part cond: atom record must preserve historical truth");
        require(!twoPartSelects(Value.record("structural-kind", "pair")),
                "two-part cond: pair record must preserve historical false");

        require(twoPartSelects(Value.record("identity-relation", "same")),
                "two-part cond: identity same must be true");
        require(!twoPartSelects(Value.record("identity-relation", "distinct")),
                "two-part cond: identity distinct must be false");
        require(twoPartSelects(Value.record("structural-relation", "same")),
                "two-part cond: structural same must be true");
        require(!twoPartSelects(Value.record("structural-relation", "distinct")),
                "two-part cond: structural distinct must be false");

        require(twoPartSelects(exact(1)), "two-part cond: exact 1 remains truthy");
        require(twoPartSelects(exact(0)),
                "two-part cond: ordinary exact 0 must preserve published G8 truthiness");
        require(twoPartSelects(exact(2)),
                "two-part cond: ordinary exact values retain legacy truthiness");
        require(!twoPartSelects(Value.NIL), "two-part cond: NIL remains false");
        require(twoPartSelects(Value.record("other-domain", "value")),
                "two-part cond: unrelated records retain legacy truthiness");

        Object pairRecord = Value.record("structural-kind", "pair");
        require(threePartSelects(pairRecord, Value.record("structural-kind", "pair")),
                "canonical three-part cond must match explicit pair record");
        require(!threePartSelects(pairRecord, Value.record("structural-kind", "atom")),
                "canonical three-part cond must ignore migration truthiness");

        require(threePartSelects(exact(0), exact(0)),
                "canonical three-part cond must match explicit exact zero");
        require(!threePartSelects(exact(0), exact(1)),
                "canonical three-part cond must not coerce exact decisions");

        // Source-level witnesses prove the Reader -> Compiler -> CondNode path
        // uses the same temporary bridge and that canonical three-part cond
        // stays explicit-result matching.
        System.setProperty("wsm.registryPath", args[0]);
        try (Context context = Context.newBuilder("wsm").build()) {
            Object twoPart =
                    context.eval(
                            "wsm",
                            "(cond ((atom (quote (a b))) (quote wrong)) "
                                    + "((quote fallback) (quote right)))");
            require(
                    "right".equals(twoPart.toString()),
                    "source two-part cond must treat structural-kind pair as false: "
                            + twoPart);

            Object zero =
                    context.eval(
                            "wsm",
                            "(cond (0 (quote zero-truthy)) "
                                    + "((quote fallback) (quote wrong)))");
            require(
                    "zero-truthy".equals(zero.toString()),
                    "source two-part cond must preserve ordinary numeric zero truthiness: "
                            + zero);

            Object canonical =
                    context.eval(
                            "wsm",
                            "(cond ((atom (quote (a b))) (structural-kind pair) "
                                    + "(quote canonical-pair)) "
                                    + "((quote fallback) fallback (quote wrong)))");
            require(
                    "canonical-pair".equals(canonical.toString()),
                    "source three-part cond must match explicit pair record: "
                            + canonical);
        }

        System.out.println("MIGRATION-COND-TRUTHINESS-CONTRACT-OK");
    }
}
