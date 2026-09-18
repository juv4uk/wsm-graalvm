package wsm.graalvm;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Semantics-blind bootstrap projection from one live Lisp value to every
 * admitted public surface of the same numeric semantic identity.
 *
 * Surface admission comes only from CanonRegistry (pinned my-lisp registry).
 * This class never owns spelling meaning and never overwrites an existing
 * binding with a different live value.
 */
final class RegistryPeerInstaller {
    private RegistryPeerInstaller() {}

    static Set<String> installValue(
            CanonRegistry registry,
            GlobalBindings globals,
            String semanticId,
            Object liveValue) {
        CanonRegistry.Row row = registry.row(semanticId);
        LinkedHashSet<String> peers = new LinkedHashSet<>();

        for (String spelling : row.surfaces().values()) {
            if (spelling == null || spelling.isBlank() || semanticId.equals(spelling)) {
                continue;
            }
            if (!semanticId.equals(registry.semanticIdForToken(spelling))) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "registry surface escaped semantic " + semanticId + ": " + spelling);
            }
            if (!peers.add(spelling)) continue;

            if (globals.isDeclared(spelling)) {
                Object existing = globals.lookup(spelling);
                if (existing != liveValue) {
                    throw new WsmError(
                            WsmError.Kind.INVALID_FORM,
                            "registry peer already bound to a different live value: "
                                    + semanticId + " " + spelling);
                }
            } else {
                globals.define(spelling, liveValue);
            }

            if (liveValue instanceof GlobalBindings.MacroValue macro) {
                if (globals.isMacro(spelling)) {
                    if (globals.macro(spelling) != macro) {
                        throw new WsmError(
                                WsmError.Kind.INVALID_FORM,
                                "registry macro peer already bound to a different MacroValue: "
                                        + semanticId + " " + spelling);
                    }
                } else {
                    globals.defineMacro(spelling, macro);
                }
            }
        }

        return Set.copyOf(peers);
    }

    /**
     * Snapshot every registry identity that already has at least one live
     * admitted binding. Missing peers receive that exact object. This is called
     * only after pinned authority source loads, never after ordinary user eval.
     */
    static Set<String> materializeBoundValuePeers(
            CanonRegistry registry,
            GlobalBindings globals) {
        LinkedHashSet<String> materializedIds = new LinkedHashSet<>();

        for (String semanticId : registry.ids()) {
            CanonRegistry.Row row = registry.row(semanticId);
            Object liveValue = null;
            boolean found = false;

            LinkedHashSet<String> peerNames =
                    new LinkedHashSet<>(row.surfaces().values());

            for (String spelling : peerNames) {
                if (spelling == null || spelling.isBlank() || semanticId.equals(spelling)) {
                    continue;
                }
                if (!globals.isDeclared(spelling)) continue;

                Object existing;
                try {
                    existing = globals.lookup(spelling);
                } catch (WsmError error) {
                    if (error.kind == WsmError.Kind.UNKNOWN_SYMBOL) {
                        continue;
                    }
                    throw error;
                }

                if (!found) {
                    liveValue = existing;
                    found = true;
                } else if (existing != liveValue) {
                    throw new WsmError(
                            WsmError.Kind.INVALID_FORM,
                            "multiple admitted peers already hold different live values: "
                                    + semanticId);
                }
            }

            if (!found) continue;

            installValue(registry, globals, semanticId, liveValue);
            materializedIds.add(semanticId);
        }

        return Set.copyOf(materializedIds);
    }
}
