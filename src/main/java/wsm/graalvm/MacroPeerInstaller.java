package wsm.graalvm;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Installs the Lisp-owned macro-definition value on every admitted public
 * surface of semantic identity 0012.
 *
 * This is bootstrap plumbing only. The macro meaning comes from
 * external/my-lisp/lib/macro.lisp; this class never implements DEFMACRO.
 */
final class MacroPeerInstaller {
    static final String DEFMACRO_ID = "0012";

    private MacroPeerInstaller() {}

    static Set<String> install(
            CanonRegistry registry,
            GlobalBindings globals,
            GlobalBindings.MacroValue macro) {
        CanonRegistry.Row row = registry.row(DEFMACRO_ID);
        Set<String> installed = new LinkedHashSet<>();

        for (String spelling : row.surfaces().values()) {
            if (spelling == null || spelling.isBlank() || DEFMACRO_ID.equals(spelling)) {
                continue;
            }
            if (!DEFMACRO_ID.equals(registry.semanticIdForToken(spelling))) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "registry surface escaped semantic 0012: " + spelling);
            }
            if (installed.add(spelling)) {
                globals.defineMacro(spelling, macro);
            }
        }

        if (installed.isEmpty()) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "semantic 0012 has no admitted public macro surfaces");
        }
        return Set.copyOf(installed);
    }
}
