package wsm.graalvm;

import java.util.Set;

/**
 * 00001010 bootstrap wrapper over the generic registry peer installer.
 *
 * The MacroValue meaning comes from external/sens/lib/macro.lisp; this
 * class names only the numeric bootstrap identity required by that source.
 */
final class MacroPeerInstaller {
    static final Sid8 DEFMACRO_ID = Sid8.bits(0,0,0,0,1,0,1,0);

    private MacroPeerInstaller() {}

    static Set<String> install(
            CanonRegistry registry,
            GlobalBindings globals,
            GlobalBindings.MacroValue macro) {
        Set<String> installed =
                RegistryPeerInstaller.installValue(
                        registry, globals, DEFMACRO_ID, macro);
        if (installed.isEmpty()) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "semantic 00001010 has no admitted public macro surfaces");
        }
        return installed;
    }
}
