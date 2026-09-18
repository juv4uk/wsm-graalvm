package wsm.graalvm;

import java.util.List;

/**
 * Issue #47: numeric heads of admitted machine IDs route through registry
 * identity, unknown numeric heads stay data. Rule source = pinned registry.
 */
public final class NumericHeadRouteContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: NumericHeadRouteContract <semantic-registry.lisp>");
        }
        CanonRegistry registry = CanonRegistryLoader.load(args[0]);
        Compiler compiler = new Compiler(registry);

        // 1043 = string-append (stable, 10xx band). Admitted numeric head routes
        // to the substrate mechanism (the same identity the spelling surface uses).
        Object routed = evalOne(compiler, "(1043 \"ліве\" \"праве\")");
        require("лівеправе".equals(rawText(routed)),
                "1043 head must route to its admitted mechanism; got " + routed);

        // 1999 = not admitted -> stays UnknownSymbol; no silent route
        String error = "none";
        try {
            evalOne(compiler, "(1999 1)");
        } catch (WsmError e) {
            error = e.kind.name();
        }
        require("UNKNOWN_SYMBOL".equals(error) || "TYPE".equals(error),
                "non-admitted numeric head must fail as data, got " + error);

        System.out.println("NUMERIC-HEAD-ROUTE-CONTRACT-OK");
    }

    private static Object evalOne(Compiler compiler, String expr) {
        Object last = Value.NIL;
        List<WsmNode> program = compiler.compileProgram(new Reader(expr).readAll());
        for (WsmNode node : program) last = node.executeGeneric(null);
        return last;
    }

    private static String rawText(Object v) {
        if (v instanceof Value.Str s) return s.value;
        return String.valueOf(v);
    }
}
