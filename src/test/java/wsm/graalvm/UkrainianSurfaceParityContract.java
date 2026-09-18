package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        int rows = 0;
        int ukStable = 0;
        int ukrStable = 0;
        int ukCandidate = 0;
        int ukrCandidate = 0;
        int candidateOnlyUnadmitted = 0;
        int candidateAlsoStable = 0;

        for (int i = 1; i < top.size(); i++) {
            List<?> row = list(top.get(i), "registry row");
            require(!row.isEmpty(), "empty registry row");
            String id = atom(row.get(0), "semantic id");
            rows++;

            require(id.equals(registry.semanticIdForToken(id)),
                    "opaque numeric ID route missing: " + id);

            Set<String> admittedSpellings = new HashSet<>();
            java.util.ArrayList<Surface> surfaces = new java.util.ArrayList<>();

            for (int j = 1; j < row.size(); j++) {
                Surface s = surface(row.get(j), id);
                surfaces.add(s);
                if (!"—".equals(s.spelling()) && admitted(s.status())) {
                    admittedSpellings.add(s.spelling());
                }
            }

            boolean sawUk = false;
            boolean sawUkr = false;

            for (Surface s : surfaces) {
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

                    // A candidate marker may reuse a spelling already admitted
                    // by another stable/compatibility marker on the SAME ID.
                    // That token is admitted by the stable peer, not by candidate status.
                    if (admittedSpellings.contains(s.spelling())) {
                        require(id.equals(resolved),
                                "candidate/stable shared spelling escaped its ID "
                                        + id + ": " + s.spelling());
                        candidateAlsoStable++;
                    } else {
                        require(resolved == null,
                                "candidate-only surface became admitted: "
                                        + s.marker() + " " + id + " " + s.spelling()
                                        + " -> " + resolved);
                        candidateOnlyUnadmitted++;
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
                        + " (candidate-also-stable " + candidateAlsoStable + ")"
                        + " (status pass))");
    }
}
