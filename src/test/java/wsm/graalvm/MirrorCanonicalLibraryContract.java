package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Source-level smoke witness for the path-identical Lisp mirror.
 *
 * It intentionally checks parsing and compilation only: semantic execution
 * remains covered by the normal bootstrap/evaluator witnesses.
 */
public final class MirrorCanonicalLibraryContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: MirrorCanonicalLibraryContract <wsm-graalvm-root>");
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path mirror = root.resolve("mirror/my-lisp").normalize();

        String registrySource = Files.readString(
                mirror.resolve("lib/surface/semantic-registry.lisp"));
        CanonRegistry registry = new CanonRegistry().load(registrySource);
        Compiler compiler = new Compiler(registry);

        List<Path> sources = List.of(
                mirror.resolve("lib/canon.lisp"),
                mirror.resolve("lib/macro.lisp"),
                mirror.resolve("lib/core.lisp"));

        int totalForms = 0;
        for (Path source : sources) {
            require(Files.isRegularFile(source),
                    "mirror source missing: " + source);
            List<Object> forms = new Reader(Files.readString(source)).readAll();
            require(!forms.isEmpty(), "mirror source is empty: " + source);
            compiler.compileProgram(forms);
            totalForms += forms.size();
        }

        System.out.println("MIRROR-CANONICAL-LIBRARY-COMPILE-OK");
        System.out.println("  sources=" + sources.size());
        System.out.println("  forms=" + totalForms);
    }
}
