package wsm.graalvm;

import java.math.BigInteger;

/** Focused witness for the migration-only two-part cond bridge. */
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

        require(twoPartSelects(exact(1)), "two-part cond: exact 1 must be true");
        require(!twoPartSelects(exact(0)), "two-part cond: exact 0 must be false");
        require(twoPartSelects(exact(2)),
                "two-part cond: non-decision exact values retain legacy truthiness");
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

        System.out.println("MIGRATION-COND-TRUTHINESS-CONTRACT-OK");
    }
}
