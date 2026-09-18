package wsm.graalvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transport-only loader for the pinned my-lisp bootstrap closure.
 *
 * Semantic authority remains in external/my-lisp. This class only reads the
 * consumer manifest, validates that selected sources stay inside that submodule,
 * and preserves the declared source order.
 */
final class BootstrapClosureLoader {
    record Source(Path path, String text) {}

    record Closure(Path registryPath, List<Source> executableSources) {
        Closure {
            executableSources = List.copyOf(executableSources);
        }
    }

    private static final Pattern LISP_PATH =
            Pattern.compile("\"([^\"]+\\.lisp)\"");

    private BootstrapClosureLoader() {}

    static Closure load(Path repositoryRoot) throws IOException {
        Path repo = repositoryRoot.toAbsolutePath().normalize();
        Path manifest = repo.resolve("refs/lisp-dependency-manifest.lisp");
        Path authorityRoot = repo.resolve("external/my-lisp");
        return load(manifest, authorityRoot);
    }

    static Closure load(Path manifest, Path authorityRoot) throws IOException {
        Path manifestPath = manifest.toAbsolutePath().normalize();
        Path authority = authorityRoot.toAbsolutePath().normalize();

        requireRegularFile(manifestPath, "dependency manifest");
        if (!Files.isDirectory(authority)) {
            throw new IOException("bootstrap authority submodule missing: " + authority);
        }

        List<String> order = readLoadOrder(Files.readString(manifestPath));
        if (order.size() < 2) {
            throw new IOException(
                    "bootstrap manifest load-order must contain registry plus executable sources");
        }

        Path registry = resolveInsideAuthority(authority, order.get(0));
        requireRegularFile(registry, "registry authority");

        List<Source> executable = new ArrayList<>();
        for (int i = 1; i < order.size(); i++) {
            Path source = resolveInsideAuthority(authority, order.get(i));
            requireRegularFile(source, "bootstrap source");
            executable.add(new Source(source, Files.readString(source)));
        }

        return new Closure(registry, executable);
    }

    private static List<String> readLoadOrder(String manifest) throws IOException {
        List<String> out = new ArrayList<>();
        boolean inLoadOrder = false;

        for (String line : manifest.lines().toList()) {
            String trimmed = line.trim();
            if (trimmed.equals("(load-order")) {
                inLoadOrder = true;
                continue;
            }
            if (!inLoadOrder) continue;

            if (trimmed.startsWith("(witness")) break;

            Matcher matcher = LISP_PATH.matcher(line);
            if (matcher.find()) out.add(matcher.group(1));
        }

        if (out.isEmpty()) {
            throw new IOException("dependency manifest has no load-order entries");
        }
        return out;
    }

    private static Path resolveInsideAuthority(Path authority, String relative)
            throws IOException {
        Path rel = Path.of(relative);
        if (rel.isAbsolute()) {
            throw new IOException("absolute bootstrap path rejected: " + relative);
        }

        Path resolved = authority.resolve(rel).normalize();
        if (!resolved.startsWith(authority)) {
            throw new IOException("bootstrap path escapes authority submodule: " + relative);
        }
        return resolved;
    }

    private static void requireRegularFile(Path path, String role) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(role + " missing: " + path);
        }
    }
}
