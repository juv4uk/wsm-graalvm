package wsm.graalvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** M0 launcher: run a file (usually ../my-lisp/lib/canon.lisp) on the Truffle substrate. */
public final class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: wsm-graalvm <language-file.lisp> <registry.lisp> <root>");
            System.exit(2);
        }
        String file = args[0];
        String registry = args[1];
        Path rootDir = Path.of(args[2]);

        // M0 bridge: WsmLanguage.parse currently consumes the same registry path
        // as a system property. Keep the Polyglot option too so the eventual
        // context-owned option migration does not change the launcher surface.
        System.setProperty("wsm.registryPath", registry);
        try (org.graalvm.polyglot.Context context = org.graalvm.polyglot.Context.newBuilder("wsm")
                .option("wsm.registryPath", registry)
                .allowHostAccess(org.graalvm.polyglot.HostAccess.ALL)
                .build()) {
            String code = Files.readString(Path.of(file));
            System.setProperty("wsm.rootDir", rootDir.toString());
            org.graalvm.polyglot.Value result = context.eval(
                    org.graalvm.polyglot.Source.newBuilder("wsm", code, file).buildLiteral());
            if (!result.isHostObject()) {
                // values are plain host objects under the hood for M0
            }
        } catch (IOException e) {
            System.err.println("io: " + e.getMessage());
            System.exit(1);
        } catch (WsmError e) {
            System.err.println("error-kind=" + e.contractKind() + ": " + e.detail);
            System.exit(1);
        }
    }
}
