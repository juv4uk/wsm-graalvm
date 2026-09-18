package wsm.graalvm;

/** Runtime state owned by one Truffle language context. */
final class WsmContext {
    private final String registryPath;
    private CanonRegistry registry;
    private final GlobalBindings globals;

    WsmContext(String registryPath) {
        if (registryPath == null || registryPath.isBlank()) {
            throw new IllegalArgumentException(
                    "missing system property wsm.registryPath (numeric registry authority)");
        }
        this.registryPath = registryPath;
        this.globals = new GlobalBindings();
    }

    void initialize() {
        this.registry = CanonRegistryLoader.load(registryPath);
        // The pinned my-lisp macro layer explicitly requires this narrow host
        // substrate before lib/macro.lisp is evaluated. It introduces no
        // public semantic ID or macro-expansion rule.
        BootstrapMacroSubstrate.install(globals);
    }

    CanonRegistry registry() {
        if (registry == null) {
            throw new IllegalStateException("WSM context registry is not initialized");
        }
        return registry;
    }

    GlobalBindings globals() { return globals; }
}
