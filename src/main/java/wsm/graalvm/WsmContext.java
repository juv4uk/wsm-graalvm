package wsm.graalvm;

import java.io.IOException;

/** Runtime state owned by one Truffle language context. */
final class WsmContext {
    private final CanonRegistry registry;
    private final GlobalBindings globals;

    WsmContext(String registryPath) {
        if (registryPath == null || registryPath.isBlank()) {
            throw new IllegalArgumentException(
                    "missing system property wsm.registryPath (numeric registry authority)");
        }
        try {
            this.registry = CanonRegistryLoader.load(registryPath);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "cannot load numeric registry authority: " + registryPath, e);
        }
        this.globals = new GlobalBindings();
    }

    CanonRegistry registry() { return registry; }
    GlobalBindings globals() { return globals; }
}
