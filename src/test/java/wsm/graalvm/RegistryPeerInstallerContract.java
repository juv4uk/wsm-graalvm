package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Focused mechanism contract for registry-derived live-value peer snapshots. */
public final class RegistryPeerInstallerContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String requiredSurface(
            CanonRegistry registry,
            Sid8 id,
            String marker) {
        String spelling = registry.row(id).surfaces().get(marker);
        if (spelling == null || spelling.isBlank()) {
            throw new AssertionError("missing admitted " + marker + " surface for " + id);
        }
        return spelling;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: RegistryPeerInstallerContract <semantic-registry.lisp>");
        }

        CanonRegistry registry =
                CanonRegistryLoader.fromText(Files.readString(Path.of(args[0])));

        // Exact SID 00100010 has no Java mechanism on the current substrate,
        // so it is a suitable identity for live-value peer plumbing.
        Sid8 id = Sid8.bits(0,0,1,0,0,0,1,0);
        String en = requiredSurface(registry, id, "en");
        String uk = requiredSurface(registry, id, "ук");
        require(!en.equals(uk), "00100010 EN/UK witness requires distinct spellings");

        GlobalBindings globals = new GlobalBindings();
        Object liveValue = new Object();
        globals.define(en, liveValue);

        Set<Sid8> peers =
                RegistryPeerInstaller.materializeBoundValuePeers(registry, globals);

        require(peers.contains(id), "materializer did not report semantic " + id);
        require(globals.lookup(en) == liveValue, "source binding changed during materialization");
        require(globals.lookup(uk) == liveValue, "UK peer did not receive exact live object");

        // Snapshot law: a later ordinary shadow of one spelling does not
        // retarget the already-materialized peer.
        Object laterShadow = new Object();
        globals.define(uk, laterShadow);
        require(globals.lookup(uk) == laterShadow, "later UK shadow did not take effect");
        require(globals.lookup(en) == liveValue, "later UK shadow retargeted EN peer");

        // Exact SID 00001010 macro installation uses the same peer machinery.
        GlobalBindings macroGlobals = new GlobalBindings();
        GlobalBindings.MacroValue macro =
                new GlobalBindings.MacroValue(args2 -> Value.NIL);
        Set<String> macroPeers =
                RegistryPeerInstaller.installValue(
                        registry, macroGlobals, MacroPeerInstaller.DEFMACRO_ID, macro);

        Set<String> expectedMacroPeers =
                new LinkedHashSet<>(registry.row(Sid8.bits(0,0,0,0,1,0,1,0)).surfaces().values());

        require(macroPeers.equals(expectedMacroPeers),
                "generic 00001010 peer set differs from registry");
        for (String spelling : expectedMacroPeers) {
            require(macroGlobals.lookup(spelling) == macro,
                    "00001010 value peer is not exact same MacroValue: " + spelling);
            require(macroGlobals.isMacro(spelling),
                    "00001010 macro dispatch peer missing: " + spelling);
            require(macroGlobals.macro(spelling) == macro,
                    "00001010 macro peer is not exact same MacroValue: " + spelling);
        }

        System.out.println(
                "REGISTRY-PEER-INSTALLER-OK id=" + id
                        + " peers=" + registry.row(id).surfaces().size()
                        + " macro-peers=" + macroPeers.size());
    }
}
