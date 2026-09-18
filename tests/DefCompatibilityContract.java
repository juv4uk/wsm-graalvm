package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Compatibility identity 1000 stays distinct, but uses the same Define
 * execution mechanism as stable identity 0011.
 */
public final class DefCompatibilityContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Object eval(CanonRegistry registry, String source) {
        Compiler compiler = new Compiler(registry);
        ProgramBodyNode body = new ProgramBodyNode(
                compiler.compileProgram(new Reader(source).readAll()));
        return new WsmLanguage.BodyRoot(null, body).getCallTarget().call();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: DefCompatibilityContract <semantic-registry.lisp>");
        }

        CanonRegistry registry =
                new CanonRegistry().load(Files.readString(Path.of(args[0])));

        require("1000".equals(registry.semanticIdForToken("def")),
                "compatibility def must preserve semantic ID 1000");
        require("0011".equals(registry.semanticIdForToken("define")),
                "stable define must preserve semantic ID 0011");
        require(!registry.semanticIdForToken("def")
                        .equals(registry.semanticIdForToken("define")),
                "1000 and 0011 identities must not be collapsed");

        Object viaCompatSurface = eval(registry, "(def x 5) x");
        require(viaCompatSurface instanceof Long n && n == 5L,
                "def surface did not use Define mechanism");

        Object viaCompatId = eval(registry, "(1000 x 6) x");
        require(viaCompatId instanceof Long n && n == 6L,
                "semantic ID 1000 did not use Define mechanism");

        Object viaStableId = eval(registry, "(0011 x 7) x");
        require(viaStableId instanceof Long n && n == 7L,
                "semantic ID 0011 did not use Define mechanism");

        System.out.println("DEF-COMPATIBILITY-CONTRACT-OK");
    }
}
