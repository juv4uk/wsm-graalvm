package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;

/** Executable acceptance contract for GitHub #6. */
public final class LexicalFrameContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Object eval(CanonRegistry registry, String source) {
        Compiler compiler = new Compiler(registry);
        ProgramBodyNode body = new ProgramBodyNode(
                compiler.compileProgram(new Reader(source).readAll()));
        WsmLanguage.BodyRoot root = new WsmLanguage.BodyRoot(null, body);
        return root.getCallTarget().call();
    }

    private static WsmError expectInvalid(
            CanonRegistry registry,
            String source) {
        try {
            eval(registry, source);
            throw new AssertionError("expected InvalidForm: " + source);
        } catch (WsmError error) {
            require(error.kind == WsmError.Kind.INVALID_FORM,
                    "expected InvalidForm, got " + error.contractKind()
                            + " for " + source);
            return error;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: LexicalFrameContract <semantic-registry.lisp>");
        }

        CanonRegistry registry =
                new CanonRegistry().load(Files.readString(Path.of(args[0])));

        Object simple = eval(registry, "((0010 (x) x) 42)");
        require(simple instanceof Long n && n == 42L,
                "depth-0 parameter frame failed");

        Object captured = eval(
                registry,
                "(((0010 (x) (0010 (y) x)) 41) 99)");
        require(captured instanceof Long n && n == 41L,
                "depth-1 lexical capture failed");

        Object deep = eval(
                registry,
                "((((0010 (x) (0010 (y) (0010 (z) x))) 11) 22) 33)");
        require(deep instanceof Long n && n == 11L,
                "depth-2 lexical capture failed");

        // Contract 6.0 protects only Canon 0+7. A non-Canon registry
        // operation name remains an ordinary lexical binding.
        require("1035".equals(registry.semanticIdForToken("second")),
                "real registry no longer maps second to expected non-Canon ID");
        Object shadowed = eval(
                registry,
                "((0010 (second) second) 7)");
        require(shadowed instanceof Long n && n == 7L,
                "non-Canon registry surface must remain lexically shadowable");

        // Canon 0005 is immutable under every admitted surface.
        expectInvalid(registry, "(0010 (0005) 0005)");
        expectInvalid(registry, "(0010 (car) car)");
        expectInvalid(registry, "(0010 (перше) перше)");
        expectInvalid(registry, "(0010 (ādi) ādi)");

        // Shared top-level definition: declare-before-value compilation
        // enables recursion/reference while runtime value remains global.
        Object global = eval(
                registry,
                "(0011 identity (0010 (x) x)) (identity 9)");
        require(global instanceof Long n && n == 9L,
                "shared top-level definition failed");

        System.out.println("LEXICAL-FRAME-CONTRACT-OK");
    }
}
