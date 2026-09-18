package wsm.graalvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * M0 launcher for WSM/my-lisp source files on the Truffle substrate.
 *
 * <p>Files are evaluated sequentially in one runtime context.  The last two
 * arguments are the semantic registry and the my-lisp root directory; all
 * preceding arguments are source files.  This keeps the CLI stable for a single
 * file while also supporting the full bootstrap chain canon &rarr; macro &rarr;
 * core &rarr; user program.</p>
 */
public final class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("usage: wsm-graalvm <language-file.lisp>... <registry.lisp> <root>");
            System.exit(2);
        }

        String registry = args[args.length - 2];

        WsmContext context = new WsmContext(registry);
        context.initialize();

        try {
            for (int i = 0; i < args.length - 2; i++) {
                String file = args[i];
                String code = Files.readString(Path.of(file));
                Object result = BootstrapRuntime.execute(context, code);
                if (result instanceof GlobalBindings.MacroValue macro
                        && file.endsWith("macro.lisp")) {
                    MacroPeerInstaller.install(context.registry(), context.globals(), macro);
                }
                System.out.println("[wsm-graalvm M0] result: " + Printer.print(result));
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
