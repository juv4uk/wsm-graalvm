package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry-derived Ukrainian surface admission contract for the current
 * exact-byte registry schema.
 *
 * The test owns no Ukrainian spelling table. Non-empty `ук` / `укр`
 * surfaces are admitted directly by the pinned semantic registry; `()`
 * means that the namespace has no spelling for that SID.
 */
public final class UkrainianSurfaceParityContract {
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

    private static String spelling(Object value, String id, String marker) {
        if (value instanceof String s) return s;
        if (value instanceof List<?> missing && missing.isEmpty()) return null;
        throw new AssertionError(
                "surface must be spelling or () for " + id + " " + marker + ": " + value);
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
        require(top.size() >= 2, "registry must contain header and rows");

        List<?> header = list(top.get(0), "registry header");
        require(header.size() == 2
                        && "binary".equals(atom(header.get(0), "header kind"))
                        && "8".equals(atom(header.get(1), "header width")),
                "registry header must be (binary 8)");

        Map<String, String> admittedOwner = new LinkedHashMap<>();
        int rows = 0;
        int ukPresent = 0;
        int ukMissing = 0;
        int ukrPresent = 0;
        int ukrMissing = 0;

        for (int i = 1; i < top.size(); i++) {
            List<?> row = list(top.get(i), "registry row");
            require(!row.isEmpty(), "empty registry row");
            String id = atom(row.get(0), "semantic SID");
            require(id.matches("[01]{8}"), "SID must be exactly 8 bits: " + id);
            require(id.equals(registry.semanticIdForToken(id)),
                    "exact SID route missing: " + id);
            rows++;

            boolean sawUk = false;
            boolean sawUkr = false;

            for (int j = 1; j < row.size(); j++) {
                List<?> surface = list(row.get(j), "surface for " + id);
                require(surface.size() == 2, "surface must have marker + spelling: " + surface);
                String marker = atom(surface.get(0), "surface marker");
                String value = spelling(surface.get(1), id, marker);

                if (value != null) {
                    String previous = admittedOwner.putIfAbsent(value, id);
                    require(previous == null || previous.equals(id),
                            "admitted spelling collision: " + value
                                    + " -> " + previous + " and " + id);
                    require(id.equals(registry.semanticIdForToken(value)),
                            "surface did not resolve to " + id + ": " + value);
                }

                if ("ук".equals(marker)) {
                    sawUk = true;
                    if (value == null) ukMissing++; else ukPresent++;
                } else if ("укр".equals(marker)) {
                    sawUkr = true;
                    if (value == null) ukrMissing++; else ukrPresent++;
                }
            }

            require(sawUk, "registry row missing ук marker: " + id);
            require(sawUkr, "registry row missing укр marker: " + id);
        }

        require(rows == registry.ids().size(),
                "parsed row count differs from CanonRegistry row count");

        System.out.println(
                "(uk-surface-parity"
                        + " (rows " + rows + ")"
                        + " (ук-present " + ukPresent + ")"
                        + " (ук-missing " + ukMissing + ")"
                        + " (укр-present " + ukrPresent + ")"
                        + " (укр-missing " + ukrMissing + ")"
                        + " (status pass))");
    }
}
