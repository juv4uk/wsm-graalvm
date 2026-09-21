package wsm.graalvm;

import java.util.List;

/**
 * #230 current identity rule: exact eight-bit SID spellings route through the
 * registry. Decimal numbers are ordinary numeric values and must never be
 * reconstructed into a semantic identity.
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

        // Current string-append identity. Its exact bit spelling is a token,
        // not a decimal number, and routes exactly as the human surfaces do.
        Object routed = evalOne(compiler, "(00111010 \"ліве\" \"праве\")");
        require("лівеправе".equals(rawText(routed)),
                "00111010 head must route to its admitted mechanism; got " + routed);

        // Decimal 58 is the numeric value of 00111010, but must NOT become SID.
        expectNotCallable(compiler, "(58 \"ліве\" \"праве\")", "decimal 58");

        // Historical decimal-looking ID must also remain ordinary numeric data.
        expectNotCallable(compiler, "(1043 \"ліве\" \"праве\")", "legacy 1043");

        System.out.println("NUMERIC-HEAD-ROUTE-CONTRACT-OK exact-byte-only");
    }

    private static void expectNotCallable(
            Compiler compiler,
            String expr,
            String label) {
        String error = "none";
        try {
            evalOne(compiler, expr);
        } catch (WsmError e) {
            error = e.kind.name();
        }
        require("TYPE".equals(error) || "UNKNOWN_SYMBOL".equals(error),
                label + " must not route as SID, got " + error);
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
