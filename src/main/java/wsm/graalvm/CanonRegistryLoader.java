package wsm.graalvm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Loads the numeric registry authority once per host run (cacheable). */
public final class CanonRegistryLoader {
    private static final Map<String, CanonRegistry> CACHE = new HashMap<>();

    private CanonRegistryLoader() {}

    public static synchronized CanonRegistry load(String path) {
        return CACHE.computeIfAbsent(path, p -> {
            try {
                return fromText(Files.readString(Path.of(p)));
            } catch (IOException io) {
                throw new WsmError(WsmError.Kind.PARSE, "registry unreadable: " + path);
            }
        });
    }

    static CanonRegistry fromText(String text) {
        return new CanonRegistry().load(text);
    }
}
