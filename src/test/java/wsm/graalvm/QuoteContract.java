package wsm.graalvm;

import java.util.LinkedHashSet;
import java.util.Set;

/** Executable evidence for #8: quote sugar and semantic identity 0001. */
public final class QuoteContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object eval(Compiler compiler, String source) {
        Object last = Value.NIL;
        for (WsmNode node : compiler.compileProgram(new Reader(source).readAll())) {
            last = node.executeGeneric(null);
        }
        return last;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: QuoteContract <semantic-registry.lisp>");
        }

        CanonRegistry registry = CanonRegistryLoader.load(args[0]);
        Compiler compiler = new Compiler(registry);

        Object sugar = eval(compiler, "'surface-probe");
        Object numeric = eval(compiler, "(0001 surface-probe)");
        require(WsmNode.Structural.equals(sugar, numeric),
                "apostrophe sugar must lower to the same observable datum as 0001");

        Set<String> surfaces = new LinkedHashSet<>(
                registry.row("0001").surfaces().values());
        int checkedSurfaces = 0;
        for (String surface : surfaces) {
            if ("'".equals(surface)) continue; // reader sugar is tested above
            require("0001".equals(registry.semanticIdForToken(surface)),
                    "registry surface must resolve to 0001: " + surface);
            Object viaSurface = eval(
                    new Compiler(registry),
                    "(" + surface + " surface-probe)");
            require(WsmNode.Structural.equals(sugar, viaSurface),
                    "quote surface diverged from 0001: " + surface);
            checkedSurfaces++;
        }
        require(checkedSurfaces > 0, "expected at least one non-sugar 0001 surface");

        Object straight = new Reader("об'єкт").readAll().get(0);
        require(straight instanceof Reader.Token t
                        && t.spelling().equals("об'єкт"),
                "straight internal apostrophe must remain inside one identifier");

        Object curly = new Reader("об’єкт").readAll().get(0);
        require(curly instanceof Reader.Token t
                        && t.spelling().equals("об’єкт"),
                "curly internal apostrophe must remain inside one identifier");

        Object quotedStraight = eval(new Compiler(registry), "'об'єкт");
        require(Printer.print(quotedStraight).equals("об'єкт"),
                "quoted identifier with internal apostrophe changed spelling");

        Object dotted = eval(new Compiler(registry), "'(a b . c)");
        require(Printer.print(dotted).equals("(a b . c)"),
                "quoted dotted datum must preserve exact pair shape");

        Object proper = eval(new Compiler(registry), "'(a b c)");
        require(Printer.print(proper).equals("(a b c)"),
                "quoted proper list must preserve exact list shape");

        Object nested = eval(new Compiler(registry), "''x");
        require(Printer.print(nested).equals("(0001 x)"),
                "nested quote must materialize quote identity as data, not a human spelling");

        Object empty = eval(new Compiler(registry), "'()");
        require(empty == Value.NIL, "quoted empty list must preserve Canon 0");

        System.out.println(
                "QUOTE-CONTRACT-OK surfaces=" + checkedSurfaces
                        + " apostrophe=leading/internal dotted=preserved");
    }
}
