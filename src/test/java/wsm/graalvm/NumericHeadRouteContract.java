package wsm.graalvm;

import java.util.List;

/**
 * #233 retirement witness for the historical numeric-ID route.
 *
 * Exact 8-bit SID spellings are semantic identities. Decimal integers are
 * ordinary numeric data/call targets and must never be reinterpreted as SIDs.
 */
public final class NumericHeadRouteContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: NumericHeadRouteContract <semantic-registry.lisp>");
        }
        CanonRegistry registry = CanonRegistryLoader.load(args[0]);
        Compiler compiler = new Compiler(registry);

        // Current string-append SID is the exact bit spelling 00111010.
        Object routed = evalOne(compiler, "(00111010 \"ліве\" \"праве\")");
        require("лівеправе".equals(rawText(routed)),
                "exact byte SID 00111010 must route to its substrate mechanism; got " + routed);

        require("00111010".equals(registry.semanticIdForToken("00111010")),
                "exact 8-bit SID must resolve to itself");
        require(registry.semanticIdForToken("1043") == null,
                "historical decimal ID 1043 must not remain an admitted semantic route");

        // Historical 1043 is now an ordinary integer in call position, never
        // a semantic identity. It must fail rather than silently dispatch.
        String oldRouteError = "none";
        try {
            evalOne(compiler, "(1043 \"ліве\" \"праве\")");
        } catch (WsmError e) {
            oldRouteError = e.kind.name();
        }
        require("TYPE".equals(oldRouteError) || "INVALID_FORM".equals(oldRouteError),
                "historical decimal head must fail as a non-callable number, got "
                        + oldRouteError);

        System.out.println("EXACT-BYTE-SID-ROUTE-CONTRACT-OK");
    }

    private static Object evalOne(Compiler compiler, String expr) {
        Object last = Value.NIL;
        List<WsmNode> program = compiler.compileProgram(new Reader(expr).readAll());
        for (WsmNode node : program) last = node.executeGeneric(null);
        return last;
    }

    private static String rawText(Object v) {
        if (v instanceof Value.Str s) return s.value;
        if (v instanceof Value.StringValue s) return s.value;
        return String.valueOf(v);
    }
}
