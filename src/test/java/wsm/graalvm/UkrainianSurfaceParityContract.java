package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry-derived Ukrainian surface admission contract.
 *
 * The test owns no Ukrainian spelling table. It reads uk/ukr markers and
 * statuses from the pinned semantic-registry and exercises the normal
 * CanonRegistry resolver.
 */
public final class UkrainianSurfaceParityContract {
    private record Surface(String marker, String spelling, String status) {}

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static List<?> list(Object value, String context) {
        if (value instanceof List<?> items) return items;
        throw new AssertionError(context + " must be a list: " + value);
    }

    private static String atom(Object value, String context) {
        if (value instanceof String s) return s;
        throw new AssertionError(context + " must be an atom: " + value);
    }

    private static boolean admitted(String status) {
        return "stable".equals(status) || "compatibility-only".equals(status);
    }

    private static Surface surface(Object value, String id) {
        List<?> items = list(value, "surface for " + id);
        require(items.size() >= 3, "malformed surface for " + id + ": " + items);
        return new Surface(
                atom(items.get(0), "surface marker"),
                atom(items.get(1), "surface spelling"),
                atom(items.get(items.size() - 1), "surface status"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: UkrainianSurfaceParityContract <semantic-registry.lisp>");
        }

        String source = Files.readString(Path.of(args[0]));
        CanonRegistry registry = CanonRegistryLoader.fromText(source);
        List<Object> forms = new RegistrySexpReader(source).readAll();

        require(forms.size() == 1, "registry must contain exactly one top-level form");
        List<?> top = list(forms.get(0), "registry");
        require(!top.isEmpty() && "sr/1".equals(top.get(0)), "registry schema must be sr/1");

        // Build the globally admitted spelling owner map from authority data.
        // This distinguishes "candidate became admitted" from "candidate
        // reuses a spelling already stable for another semantic identity".
        Map<String, String> globallyAdmittedOwner = new LinkedHashMap<>();
        for (int i = 1; i < top.size(); i++) {
            List<?> row = list(top.get(i), "registry row");
            require(!row.isEmpty(), "empty registry row");
            String id = atom(row.get(0), "semantic id");

            for (int j = 1; j < row.size(); j++) {
                Surface s = surface(row.get(j), id);
                if ("—".equals(s.spelling()) || !admitted(s.status())) continue;

                String previous = globallyAdmittedOwner.putIfAbsent(s.spelling(), id);
                require(previous == null || previous.equals(id),
                        "admitted spelling collision: " + s.spelling()
                                + " -> " + previous + " and " + id);
            }
        }

        int rows = 0;
        int ukStable = 0;
        int ukrStable = 0;
        int ukCandidate = 0;
        int ukrCandidate = 0;
        int candidateOnlyUnadmitted = 0;
        int candidateAlsoStableSameId = 0;
        int candidateSpellingOwnedByOtherStableId = 0;

        for (int i = 1; i < top.size(); i++) {
            List<?> row = list(top.get(i), "registry row");
            require(!row.isEmpty(), "empty registry row");
            String id = atom(row.get(0), "semantic id");
            rows++;

            require(id.equals(registry.semanticIdForToken(id)),
                    "opaque numeric ID route missing: " + id);

            boolean sawUk = false;
            boolean sawUkr = false;

            for (int j = 1; j < row.size(); j++) {
                Surface s = surface(row.get(j), id);
                boolean isUk = "uk".equals(s.marker());
                boolean isUkr = "ukr".equals(s.marker());
                if (!isUk && !isUkr) continue;

                if (isUk) sawUk = true;
                if (isUkr) sawUkr = true;

                if ("stable".equals(s.status())) {
                    require(!"—".equals(s.spelling()),
                            s.marker() + " stable surface cannot be missing for " + id);
                    require(id.equals(registry.semanticIdForToken(s.spelling())),
                            s.marker() + " stable surface did not resolve to " + id
                                    + ": " + s.spelling());
                    if (isUk) ukStable++;
                    if (isUkr) ukrStable++;
                    continue;
                }

                if ("candidate".equals(s.status())) {
                    if (isUk) ukCandidate++;
                    if (isUkr) ukrCandidate++;
                    if ("—".equals(s.spelling())) continue;

                    String resolved = registry.semanticIdForToken(s.spelling());
                    String stableOwner = globallyAdmittedOwner.get(s.spelling());

                    if (stableOwner == null) {
                        require(resolved == null,
                                "candidate-only spelling became admitted: "
                                        + s.marker() + " " + id + " " + s.spelling()
                                        + " -> " + resolved);
                        candidateOnlyUnadmitted++;
                    } else {
                        require(stableOwner.equals(resolved),
                                "candidate spelling no longer resolves to its stable owner: "
                                        + s.spelling() + " expected " + stableOwner
                                        + " got " + resolved);
                        if (stableOwner.equals(id)) {
                            candidateAlsoStableSameId++;
                        } else {
                            candidateSpellingOwnedByOtherStableId++;
                        }
                    }
                    continue;
                }

                if ("compatibility-only".equals(s.status())
                        && !"—".equals(s.spelling())) {
                    require(id.equals(registry.semanticIdForToken(s.spelling())),
                            s.marker() + " compatibility surface did not resolve to " + id
                                    + ": " + s.spelling());
                }
            }

            require(sawUk, "registry row missing uk marker: " + id);
            require(sawUkr, "registry row missing ukr marker: " + id);
        }

        System.out.println(
                "(uk-surface-parity"
                        + " (rows " + rows + ")"
                        + " (uk-stable " + ukStable + ")"
                        + " (ukr-stable " + ukrStable + ")"
                        + " (uk-candidate " + ukCandidate + ")"
                        + " (ukr-candidate " + ukrCandidate + ")"
                        + " (candidate-only-unadmitted " + candidateOnlyUnadmitted + ")"
                        + " (candidate-also-stable-same-id "
                        + candidateAlsoStableSameId + ")"
                        + " (candidate-spelling-owned-by-other-stable-id "
                        + candidateSpellingOwnedByOtherStableId + ")"
                        + " (status pass))");
    }
}
