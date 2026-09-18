package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Gate #9.1: consume the real pinned semantic-registry.lisp, not a synthetic
 * spelling table.
 */
public final class RealRegistryContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: RealRegistryContract <semantic-registry.lisp>");
        }

        Path path = Path.of(args[0]);
        CanonRegistry registry = new CanonRegistry().load(Files.readString(path));

        for (String id : Set.of(
                "0001", "0002", "0003", "0004", "0005", "0006", "0007",
                "0010", "0011", "0012")) {
            require(registry.ids().contains(id), "real registry missing " + id);
            require(id.equals(registry.semanticIdForToken(id)),
                    "machine ID must self-resolve: " + id);
        }

        require("0005".equals(registry.semanticIdForToken("car")),
                "real EN 0005 surface mismatch");
        require("0005".equals(registry.semanticIdForToken("перше")),
                "real UK 0005 surface mismatch");
        require("0005".equals(registry.semanticIdForToken("ādi")),
                "real SA 0005 surface mismatch");
        require("0005".equals(registry.semanticIdForToken(":п")),
                "real symbolic 0005 surface mismatch");

        // Critical regression: apostrophe is a registry surface atom for 0001,
        // not quote syntax inside the registry data schema.
        require("0001".equals(registry.semanticIdForToken("'")),
                "registry apostrophe surface must map to 0001");

        require("0012".equals(registry.semanticIdForToken("defmacro-derived")),
                "compatibility-only surface must remain admitted");
        require("0011".equals(registry.semanticIdForToken("визначити")),
                "stable surface must win by registry fact, not Java spelling");

        System.out.println("REAL-REGISTRY-CONTRACT-OK " + path);
    }
}
