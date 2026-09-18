package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;

/** AST nodes. Plain subclasses (M0): compile-once, execute-on-object. */
public abstract class WsmNode extends com.oracle.truffle.api.nodes.Node {
    public abstract Object executeGeneric(VirtualFrame frame);

    public static final class ConstantNode extends WsmNode {
        private final Object value;
        ConstantNode(Object v) { this.value = v; }
        @Override public Object executeGeneric(VirtualFrame frame) { return value; }
    }

    public static final class QuoteNode extends WsmNode {
        private final Object datum;
        QuoteNode(Object datum) { this.datum = datum; }
        @Override public Object executeGeneric(VirtualFrame frame) { return datum; }
    }

    @NodeInfo(shortName = "s0")
    public static final class AtomNode extends WsmNode {
        @Child private WsmNode arg;
        AtomNode(WsmNode arg) { this.arg = arg; }
        @Override public Object executeGeneric(VirtualFrame frame) {
            return Value.structuralKind(arg.executeGeneric(frame));
        }
    }

    @NodeInfo(shortName = "call")
    public static final class CallNode extends WsmNode {
        @Child private WsmNode fn;
        @Children private final WsmNode[] args;
        CallNode(WsmNode fn, WsmNode[] args) { this.fn = fn; this.args = args; }
        @Override public Object executeGeneric(VirtualFrame frame) {
            Object f = fn.executeGeneric(frame);
            Object[] argv = new Object[args.length];
            for (int i = 0; i < args.length; i++) argv[i] = args[i].executeGeneric(frame);
            if (f instanceof WsmFunc func) return func.call(argv);
            throw new WsmError(WsmError.Kind.TYPE, "not callable: " + Printer.print(f));
        }
    }

    @NodeInfo(shortName = "bind")
    public static final class DefineNode extends WsmNode {
        private final String name;
        @Child private WsmNode value;
        private final Environment env;
        DefineNode(String name, WsmNode value, Environment env) {
            this.name = name; this.value = value; this.env = env;
        }
        @Override public Object executeGeneric(VirtualFrame frame) {
            Object v = value.executeGeneric(frame);
            env.define(name, v);
            return v;
        }
    }

    public static final class LambdaNode extends WsmNode {
        private final String[] params;
        @Children private final WsmNode[] body;
        private final Environment env;
        LambdaNode(String[] params, WsmNode[] body, Environment env) {
            this.params = params; this.body = body; this.env = env;
        }
        @Override public Object executeGeneric(VirtualFrame frame) {
            return new Closure(params, body, env);
        }
    }

    public static final class SymbolNode extends WsmNode {
        private final String name;
        private final Environment env;
        SymbolNode(String name, Environment env) { this.name = name; this.env = env; }
        @Override public Object executeGeneric(VirtualFrame frame) {
            return env.lookup(name);
        }
    }

    /** 0003: atom-only; identity-relation record; never a bool. */
    @NodeInfo(shortName = "eq")
    public static final class EqNode extends WsmNode {
        @Child private WsmNode a;
        @Child private WsmNode b;
        EqNode(WsmNode a, WsmNode b) { this.a = a; this.b = b; }
        @Override public Object executeGeneric(VirtualFrame frame) {
            return eqRecord(a.executeGeneric(frame), b.executeGeneric(frame));
        }
        public static Object eqRecord(Object x, Object y) {
            if (!Value.isAtom(x) || !Value.isAtom(y))
                throw new WsmError(WsmError.Kind.TYPE, "0003 expects two atoms");
            boolean same = x == y
                    || (x instanceof Value.Symbol sx && y instanceof Value.Symbol sy && sx.name.equals(sy.name))
                    || (x instanceof Long lx && y instanceof Long ly && lx.equals(ly));
            return same ? Value.identitySame() : Value.record("identity-relation", "distinct");
        }
    }

    @NodeInfo(shortName = "select")
    public static final class CondNode extends WsmNode {
        @Children private final WsmNode[] tests;
        @Children private final WsmNode[] expecteds;
        @Children private final WsmNode[] bodies;
        private final boolean[] migration;
        CondNode(WsmNode[] tests, WsmNode[] expecteds, WsmNode[] bodies, boolean[] migration) {
            this.tests = tests; this.expecteds = expecteds; this.bodies = bodies;
            this.migration = migration;
        }
        @Override public Object executeGeneric(VirtualFrame frame) {
            for (int i = 0; i < tests.length; i++) {
                Object got = tests[i].executeGeneric(frame);
                if (migration[i]) {
                    if (WsmNode.MigrationTruthyNode.matches(got)) return bodies[i].executeGeneric(frame);
                } else if (Structural.equals(got, expecteds[i].executeGeneric(frame))) {
                    return bodies[i].executeGeneric(frame);
                }
            }
            return Value.NIL;
        }
    }

    /** migration-only truthiness bridge: same mapping as the Rust host */
    public static final class MigrationTruthyNode extends WsmNode {
        public static final MigrationTruthyNode INSTANCE = new MigrationTruthyNode();
        private MigrationTruthyNode() {}
        @Override public Object executeGeneric(VirtualFrame frame) {
            throw new WsmError(WsmError.Kind.INVALID_FORM, "migration sentinel is data, not code");
        }
        static boolean matches(Object v) {
            if (v == Value.NIL) return false;
            if (v instanceof Value.Pair rec && rec.car instanceof Value.Symbol kind
                    && rec.cdr instanceof Value.Pair tail1
                    && tail1.car instanceof Value.Symbol state
                    && tail1.cdr instanceof Value.Pair tailEnd
                    && tailEnd.cdr == Value.NIL) {
                if (kind.name.equals("structural-kind")) return !state.name.equals("pair");
                if (kind.name.equals("identity-relation")) return state.name.equals("same");
            }
            return true;
        }
    }

    /** Deep structural equality — same observable shape as Rust Value PartialEq. */
    public static final class Structural {
        private Structural() {}
        static boolean equals(Object x, Object y) {
            if (x == y) return true;
            if (x instanceof Value.Symbol sx && y instanceof Value.Symbol sy)
                return sx.name.equals(sy.name);
            if (x instanceof Long lx && y instanceof Long ly) return lx.equals(ly);
            if (x instanceof Value.Pair px && y instanceof Value.Pair py)
                return equals(px.car, py.car) && equals(px.cdr, py.cdr);
            return false;
        }
    }
}
