package wsm.graalvm;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;

/** AST nodes. Language identity is already resolved before these nodes run. */
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

        CallNode(WsmNode fn, WsmNode[] args) {
            this.fn = fn;
            this.args = args;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            Object f = fn.executeGeneric(frame);
            Object[] argv = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                argv[i] = args[i].executeGeneric(frame);
            }

            if (f instanceof Value.SemanticRef semantic) {
                return SemanticMechanismTable.invoke(semantic.id(), argv);
            }
            if (f instanceof WsmFunc func) {
                return func.call(argv);
            }
            throw new WsmError(
                    WsmError.Kind.TYPE,
                    "not callable: " + Printer.print(f));
        }
    }

    /**
     * 1062 EVAL: evaluate a Lisp datum in the same WSM compiler/context and
     * current Truffle lexical frame. No fresh Polyglot Context or string
     * serialization boundary is introduced.
     */
    public static final class EvalNode extends WsmNode {
        @Child private WsmNode datum;
        private final Compiler compiler;
        private final LexicalScope scope;

        EvalNode(WsmNode datum, Compiler compiler, LexicalScope scope) {
            this.datum = datum;
            this.compiler = compiler;
            this.scope = scope;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            Object form = datum.executeGeneric(frame);

            // Upstream eval_values preserves already-materialized closures and
            // macros as values. They are runtime objects, not source syntax.
            if (form instanceof Closure || form instanceof GlobalBindings.MacroValue) {
                return form;
            }

            WsmNode executable = compiler.compile(form, scope);
            return executable.executeGeneric(frame);
        }
    }

    /** Read a binding from the current or an enclosing Truffle lexical frame. */
    public static final class LocalReadNode extends WsmNode {
        private final int depth;
        private final int slot;
        private final String name;

        LocalReadNode(int depth, int slot, String name) {
            this.depth = depth;
            this.slot = slot;
            this.name = name;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            Frame target = frameAtDepth(frame, depth, name);
            Object value = target.getValue(slot);
            if (value == Value.UNBOUND) {
                throw new WsmError(
                        WsmError.Kind.UNKNOWN_SYMBOL,
                        "unbound lexical symbol: " + name);
            }
            return value;
        }
    }

    /** Read a top-level binding. No compile-time Environment is captured. */
    public static final class GlobalReadNode extends WsmNode {
        private final String name;
        private final GlobalBindings globals;

        GlobalReadNode(String name, GlobalBindings globals) {
            this.name = name;
            this.globals = globals;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            return globals.lookup(name);
        }
    }

    /** Define/mutate a slot in the current lexical frame. */
    @NodeInfo(shortName = "bind-local")
    public static final class LocalDefineNode extends WsmNode {
        private final int slot;
        @Child private WsmNode value;

        LocalDefineNode(int slot, WsmNode value) {
            this.slot = slot;
            this.value = value;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            if (frame == null) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "local definition executed without a Truffle frame");
            }
            Object v = value.executeGeneric(frame);
            frame.setObject(slot, v);
            return v;
        }
    }

    /** Define/mutate a shared top-level runtime binding. */
    @NodeInfo(shortName = "bind-global")
    public static final class GlobalDefineNode extends WsmNode {
        private final String name;
        @Child private WsmNode value;
        private final GlobalBindings globals;

        GlobalDefineNode(String name, WsmNode value, GlobalBindings globals) {
            this.name = name;
            this.value = value;
            this.globals = globals;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            Object v = value.executeGeneric(frame);
            globals.define(name, v);
            return v;
        }
    }

    /**
     * Lambda expression. Its body is owned by LambdaRootNode; this node only
     * closes over the current lexical frame at runtime.
     */
    public static final class LambdaNode extends WsmNode {
        private final CallTarget target;
        private final boolean captureCurrentFrame;

        LambdaNode(CallTarget target, boolean captureCurrentFrame) {
            this.target = target;
            this.captureCurrentFrame = captureCurrentFrame;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            MaterializedFrame captured = null;
            if (captureCurrentFrame) {
                if (frame == null) {
                    throw new WsmError(
                            WsmError.Kind.INVALID_FORM,
                            "nested lambda created without a Truffle frame");
                }
                captured = frame.materialize();
            }
            return new Closure(target, captured);
        }
    }

    /** 0003: atom-only; identity-relation record; never a bool. */
    @NodeInfo(shortName = "eq")
    public static final class EqNode extends WsmNode {
        @Child private WsmNode a;
        @Child private WsmNode b;

        EqNode(WsmNode a, WsmNode b) {
            this.a = a;
            this.b = b;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            return eqRecord(a.executeGeneric(frame), b.executeGeneric(frame));
        }

        public static Object eqRecord(Object x, Object y) {
            if (!Value.isAtom(x) || !Value.isAtom(y)) {
                throw new WsmError(
                        WsmError.Kind.TYPE,
                        "0003 expects two atoms");
            }
            boolean same = x == y
                    || (x instanceof Value.Symbol sx
                        && y instanceof Value.Symbol sy
                        && sx.name.equals(sy.name))
                    || (x instanceof Value.SemanticRef sx
                        && y instanceof Value.SemanticRef sy
                        && sx.id().equals(sy.id()))
                    || (x instanceof Value.NumberValue nx
                        && y instanceof Value.NumberValue ny
                        && nx.numerator().equals(ny.numerator())
                        && nx.denominator().equals(ny.denominator()))
                    || (x instanceof Value.StringValue sx
                        && y instanceof Value.StringValue sy
                        && sx.value.equals(sy.value));
            return same
                    ? Value.identitySame()
                    : Value.record("identity-relation", "distinct");
        }
    }

    @NodeInfo(shortName = "select")
    public static final class CondNode extends WsmNode {
        @Children private final WsmNode[] tests;
        @Children private final WsmNode[] expecteds;
        @Children private final WsmNode[] bodies;

        private final boolean[] legacyTruthiness;

        CondNode(
                WsmNode[] tests,
                WsmNode[] expecteds,
                WsmNode[] bodies,
                boolean[] legacyTruthiness) {
            this.tests = tests;
            this.expecteds = expecteds;
            this.bodies = bodies;
            this.legacyTruthiness = legacyTruthiness;
        }

        @Override public Object executeGeneric(VirtualFrame frame) {
            for (int i = 0; i < tests.length; i++) {
                Object actual = tests[i].executeGeneric(frame);
                boolean selected = legacyTruthiness[i]
                        ? actual != Value.NIL
                        : Structural.equals(actual, expecteds[i].executeGeneric(frame));
                if (selected) {
                    return bodies[i].executeGeneric(frame);
                }
            }
            return Value.NIL;
        }
    }

    private static Frame frameAtDepth(
            VirtualFrame current,
            int depth,
            String name) {
        if (current == null) {
            throw new WsmError(
                    WsmError.Kind.UNKNOWN_SYMBOL,
                    "no lexical frame for symbol: " + name);
        }

        Frame target = current;
        for (int i = 0; i < depth; i++) {
            Object[] args = target.getArguments();
            if (args.length == 0
                    || !(args[0] instanceof MaterializedFrame parent)) {
                throw new WsmError(
                        WsmError.Kind.UNKNOWN_SYMBOL,
                        "lexical parent missing for symbol: " + name);
            }
            target = parent;
        }
        return target;
    }

    /** Deep structural equality — same observable shape as my-lisp values. */
    public static final class Structural {
        private Structural() {}

        static boolean equals(Object x, Object y) {
            if (x == y) return true;
            if (x instanceof Value.Symbol sx && y instanceof Value.Symbol sy) {
                return sx.name.equals(sy.name);
            }

            if (x instanceof Value.StringValue sx && y instanceof Value.StringValue sy) {
                return sx.value.equals(sy.value);
            }
            if (x instanceof Value.NumberValue nx && y instanceof Value.NumberValue ny) {
                return nx.numerator().equals(ny.numerator())
                        && nx.denominator().equals(ny.denominator());
            }
            if (x instanceof Value.SemanticRef sx
                    && y instanceof Value.SemanticRef sy) {
                return sx.id().equals(sy.id());
            }
            if (x instanceof Value.Pair px && y instanceof Value.Pair py) {
                return equals(px.car, py.car) && equals(px.cdr, py.cdr);
            }
            return false;
        }
    }
}
