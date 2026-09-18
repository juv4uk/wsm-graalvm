package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * First full Lisp-owned semantic witness:
 * real pinned canon.lisp + real pinned registry -> explicit conformance record.
 */
public final class RealCanonConformanceContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: RealCanonConformanceContract "
                            + "<semantic-registry.lisp> <canon.lisp>");
        }

        String registrySource = Files.readString(Path.of(args[0]));
        String canonSource = Files.readString(Path.of(args[1]));

        CanonRegistry registry = new CanonRegistry().load(registrySource);
        Compiler compiler = new Compiler(registry);

        // Compile one program so declaration knowledge and runtime globals are
        // shared across all real canon definitions and the final observer.
        List<Object> forms = new Reader(
                canonSource + "\n(canon-conforms?)\n").readAll();

        ProgramBodyNode body =
                new ProgramBodyNode(compiler.compileProgram(forms));
        Object result =
                new WsmLanguage.BodyRoot(null, body).getCallTarget().call();

        String printed = Printer.print(result);
        require(printed.equals("(canon-conformance satisfied)"),
                "real canon witness diverged: " + printed);

        System.out.println(
                "REAL-CANON-CONFORMANCE-OK " + printed);
    }
}
