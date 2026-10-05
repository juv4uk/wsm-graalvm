package wsm.graalvm;

import java.math.BigInteger;
import org.graalvm.polyglot.Context;

/**
 * Historical compatibility witness for the migration-only COND bridge.
 *
 * This is not current D3:110 authority. Current exact PredicateBit/COND work is
 * tracked by #272/#273. Human spellings are projected mechanically from the
 * pinned transitional registry so surface renames cannot redefine this test.
 */
public final class MigrationCondTruthinessContract {
    private static final Object SELECTED = Value.symbol("selected");

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Value.NumberValue exact(long value) {
        return Value.NumberValue.integer(BigInteger.valueOf(value));
    }

    private static String spelling(CanonRegistry registry, Sid8 id) {
        CanonRegistry.Row row = registry.row(id);
        for (String key : new String[] {"en", "sym", "uk", "ukr", "sa"}) {
            String surface = row.surfaces().get(key);
            if (surface != null && !surface.isBlank() && !id.matchesBareToken(surface)) {
                return surface;
            }
        }
        throw new AssertionError("no admitted legacy surface for " + id);
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

    private static void requireThreePartUnsatisfied(Object actual, Object expected) {
        WsmNode.CondNode node =
                new WsmNode.CondNode(
                        new WsmNode[] {new WsmNode.ConstantNode(actual)},
                        new WsmNode[] {new WsmNode.ConstantNode(expected)},
                        new WsmNode[] {new WsmNode.ConstantNode(SELECTED)},
                        new boolean[] {false});
        try {
            node.executeGeneric(null);
            throw new AssertionError("historical three-part cond must fail on exhaustion");
        } catch (WsmError error) {
            require(
                    error.kind == WsmError.Kind.UNSATISFIED_CONDITIONAL,
                    "historical three-part exhaustion must be UnsatisfiedConditional, got "
                            + error.contractKind());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: MigrationCondTruthinessContract <semantic-registry.lisp>");
        }

        CanonRegistry registry = CanonRegistryLoader.load(args[0]);
        String quote = spelling(registry, Sid8.bits(0,0,0,0,0,0,0,1));
        String atom = spelling(registry, Sid8.bits(0,0,0,0,0,0,1,0));
        String cond = spelling(registry, Sid8.bits(0,0,0,0,0,1,1,1));

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

        require(twoPartSelects(exact(1)), "two-part cond: ordinary exact 1 remains truthy");
        require(twoPartSelects(exact(0)),
                "two-part cond: ordinary exact 0 remains truthy per pinned conformance");
        require(twoPartSelects(exact(2)),
                "two-part cond: ordinary exact values retain legacy truthiness");
        require(!twoPartSelects(Value.NIL), "two-part cond: NIL remains false");
        require(twoPartSelects(Value.record("other-domain", "value")),
                "two-part cond: unrelated records retain legacy truthiness");

        Object pairRecord = Value.record("structural-kind", "pair");
        require(threePartSelects(pairRecord, Value.record("structural-kind", "pair")),
                "historical three-part cond must match explicit pair record");
        requireThreePartUnsatisfied(
                pairRecord,
                Value.record("structural-kind", "atom"));

        require(threePartSelects(exact(0), exact(0)),
                "historical three-part cond must match explicit exact zero");
        requireThreePartUnsatisfied(exact(0), exact(1));

        // Source-level witnesses prove the Reader -> Compiler -> CondNode path
        // uses the same temporary bridge and that the historical three-part
        // compatibility route stays explicit-result matching. Current D3:110
        // is a separate exact-PredicateBit route (#272/#273).
        System.setProperty("wsm.registryPath", args[0]);
        try (Context context = Context.newBuilder("wsm").build()) {
            Object twoPart =
                    context.eval(
                            "wsm",
                            "(" + cond + " ((" + atom + " (" + quote + " (a b))) "
                                    + "(" + quote + " wrong)) ((" + quote + " fallback) "
                                    + "(" + quote + " right)))");
            require(
                    "right".equals(twoPart.toString()),
                    "source two-part cond must treat structural-kind pair as false: "
                            + twoPart);

            Object zero =
                    context.eval(
                            "wsm",
                            "(" + cond + " (0 (" + quote + " zero-truthy)) "
                                    + "((" + quote + " fallback) (" + quote + " wrong)))");
            require(
                    "zero-truthy".equals(zero.toString()),
                    "source two-part cond must preserve ordinary numeric zero truthiness: "
                            + zero);

            Object canonical =
                    context.eval(
                            "wsm",
                            "(" + cond + " ((" + atom + " (" + quote + " (a b))) "
                                    + "(structural-kind pair) (" + quote + " canonical-pair)) "
                                    + "((" + quote + " fallback) fallback (" + quote + " wrong)))");
            require(
                    "canonical-pair".equals(canonical.toString()),
                    "source three-part cond must match explicit pair record: "
                            + canonical);

            try {
                context.eval(
                        "wsm",
                        "(" + cond + " ((" + quote + " radio) antenna (" + quote + " wrong)))");
                throw new AssertionError(
                        "historical source three-part cond must fail on exhaustion");
            } catch (org.graalvm.polyglot.PolyglotException error) {
                require(
                        error.getMessage().contains("UnsatisfiedConditional"),
                        "historical source three-part exhaustion must surface UnsatisfiedConditional: "
                                + error.getMessage());
            }

            try {
                context.eval("wsm", "(" + cond + ")");
                throw new AssertionError(
                        "empty historical three-part cond must fail on exhaustion");
            } catch (org.graalvm.polyglot.PolyglotException error) {
                require(
                        error.getMessage().contains("UnsatisfiedConditional"),
                        "empty historical three-part cond must surface UnsatisfiedConditional: "
                                + error.getMessage());
            }
        }

        System.out.println("MIGRATION-COND-TRUTHINESS-CONTRACT-OK");
    }
}
