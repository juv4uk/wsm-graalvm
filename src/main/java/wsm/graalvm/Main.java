package wsm.graalvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** M0 launcher for a WSM/my-lisp source file on the Truffle substrate. */
public final class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: wsm-graalvm <language-file.lisp> <registry.lisp> <root>");
            System.exit(2);
        }

        String file = args[0];
        String registry = args[1];
        Path rootDir = Path.of(args[2]);

        // Single M0 host boundary. WsmLanguage consumes these properties;
        // do not duplicate the same data as a Polyglot option.
        System.setProperty("wsm.registryPath", registry);
        System.setProperty("wsm.rootDir", rootDir.toString());

        try (org.graalvm.polyglot.Context context =
                     org.graalvm.polyglot.Context.newBuilder("wsm")
                             .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
                             .build()) {
            String code = Files.readString(Path.of(file));
            context.eval(
                    org.graalvm.polyglot.Source.newBuilder("wsm", code, file).buildLiteral());
        } catch (IOException e) {
            System.err.println("io: " + e.getMessage());
            System.exit(1);
        } catch (WsmError e) {
            System.err.println("error-kind=" + e.contractKind() + ": " + e.detail);
            System.exit(1);
        }
    }
}
