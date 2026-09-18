package wsm.graalvm;

import java.util.List;

/** Focused executable evidence for GitHub #6. */
public final class TruffleFrameContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object eval(Compiler compiler, String source) {
        Object last = Value.NIL;
        List<WsmNode> nodes = compiler.compileProgram(new Reader(source).readAll());
        for (WsmNode node : nodes) {
            last = node.executeGeneric(null);
        }
        return last;
    }

    private static void expectInvalid(Compiler compiler, String source) {
        try {
            eval(compiler, source);
            throw new AssertionError("expected InvalidForm: " + source);
        } catch (WsmError error) {
            require(error.kind == WsmError.Kind.INVALID_FORM,
                    "expected InvalidForm, got " + error.contractKind()
                            + " for " + source);
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: TruffleFrameContract <semantic-registry.lisp>");
        }

        CanonRegistry registry = CanonRegistryLoader.load(args[0]);

        Compiler direct = new Compiler(registry);
        Object directResult = eval(direct, "((0010 (x) x) 42)");
        require(directResult.equals(42L),
                "direct lambda parameter must live in runtime Truffle frame");

        Compiler nested = new Compiler(registry);
        Object nestedResult = eval(
                nested,
                "(((0010 (x) (0010 (y) x)) 41) 99)");
        require(nestedResult.equals(41L),
                "nested closure must read captured parent MaterializedFrame");

        Compiler shadow = new Compiler(registry);
        Object shadowResult = eval(
                shadow,
                "((0010 (lambda) (lambda 9)) (0010 (x) x))");
        require(shadowResult.equals(9L),
                "non-Canon lambda spelling must remain lexically shadowable");

        Compiler globals = new Compiler(registry);
        Object globalResult = eval(
                globals,
                "(0011 shared 7) ((0010 () shared))");
        require(globalResult.equals(7L),
                "top-level definition must be visible through shared globals");

        expectInvalid(new Compiler(registry), "((0010 (0005) 0005) 1)");
        expectInvalid(new Compiler(registry), "((0010 (car) car) 1)");

        System.out.println("TRUFFLE-FRAME-CONTRACT-OK");
    }
}
