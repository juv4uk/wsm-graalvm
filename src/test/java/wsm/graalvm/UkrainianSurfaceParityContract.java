package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Registry-derived Ukrainian surface admission contract.
 *
 * The test owns no Ukrainian spelling table. It reads ук/укр markers from the
 * pinned semantic-registry and exercises the normal CanonRegistry resolver.
 *
 * Current (@@230) schema:
 * <pre>
 *   ((binary 8)
 *    (00000000 (en ()) (ук ()) (укр ()) (sa ()) (sym ()))
 *    ...
 *    (10101001 (en binary) (ук двійковий) (укр двійковий) (sa ()) (sym ())))
 * </pre>
 *
 * Headerless schema is also accepted (no (binary 8) descriptor):
 * <pre>
 *   (00000000 (en ()) (ук ()) (укр ()) (sa ()) (sym ()))
 *   ...
 * </pre>
 *
 * Every row carries exactly five surfaces. An empty list () marks an absent
 * surface. Every exact eight-bit SID is its own semantic identity, and every
 * non-empty surface spelling must resolve back to that same SID.
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
        require(top.size() >= 2, "registry must declare (binary 8) plus rows");

        Object headerObject = top.get(0);
        boolean headerless;
        if (headerObject instanceof List<?> header
                && header.size() == 2
                && "binary".equals(atom(header.get(0), "header marker"))
                && "8".equals(atom(header.get(1), "header width"))) {
            headerless = false;
        } else if (headerObject instanceof List<?> firstRow
                && !firstRow.isEmpty()
                && atom(firstRow.get(0), "first semantic id").matches("[01]{8}")) {
            headerless = true;
        } else {
            throw new AssertionError("registry schema must be (binary 8) or headerless byte-SID rows");
        }

        String[] markers = {"en", "ук", "укр", "sa", "sym"};

        int rows = 0;
        int ukAdmitted = 0;
        int ukrAdmitted = 0;
        int ukAbsent = 0;
        int ukrAbsent = 0;
        int roundtripChecked = 0;

        for (int i = (headerless ? 0 : 1); i < top.size(); i++) {
            List<?> row = list(top.get(i), "registry row");
            require(!row.isEmpty(), "empty registry row");
            String id = atom(row.get(0), "semantic id");
            rows++;

            require(id.matches("[01]{8}"),
                    "semantic id must be exactly 8 bits: " + id);
            require(id.equals(registry.semanticIdForToken(id)),
                    "exact byte identity route missing: " + id);

            require(row.size() == 1 + markers.length,
                    "registry row must carry exactly four surfaces: " + id);

            boolean sawUk = false;
            boolean sawUkr = false;

            for (int m = 0; m < markers.length; m++) {
                String expectedMarker = markers[m];
                List<?> surface = list(row.get(m + 1), "surface for " + id);
                require(!surface.isEmpty(), "empty surface for " + id);
                String marker = atom(surface.get(0), "surface marker");
                require(marker.equals(expectedMarker),
                        "surface marker order wrong for " + id + ": expected "
                                + expectedMarker + " got " + marker);

                if (surface.size() == 1) {
                    // (marker) with no spelling — treat as absent, same as ().
                    if (marker.equals("ук")) sawUk = true;
                    if (marker.equals("укр")) sawUkr = true;
                    if (marker.equals("ук")) ukAbsent++;
                    if (marker.equals("укр")) ukrAbsent++;
                    continue;
                }

                require(surface.size() == 2,
                        "surface must be (marker spelling) or (marker): " + id);

                Object spellingObject = surface.get(1);
                if (spellingObject instanceof List<?> absent && absent.isEmpty()) {
                    // () marks an absent surface.
                    if (marker.equals("ук")) sawUk = true;
                    if (marker.equals("укр")) sawUkr = true;
                    if (marker.equals("ук")) ukAbsent++;
                    if (marker.equals("укр")) ukrAbsent++;
                    continue;
                }

                String spelling = atom(spellingObject, "surface spelling");
                if (spelling.equals("—")) {
                    if (marker.equals("ук")) sawUk = true;
                    if (marker.equals("укр")) sawUkr = true;
                    if (marker.equals("ук")) ukAbsent++;
                    if (marker.equals("укр")) ukrAbsent++;
                    continue;
                }

                // Every non-empty surface must resolve back to its own SID.
                require(id.equals(registry.semanticIdForToken(spelling)),
                        "surface did not resolve to its own SID: " + marker
                                + " " + id + " " + spelling);
                roundtripChecked++;

                if (marker.equals("ук")) {
                    sawUk = true;
                    ukAdmitted++;
                }
                if (marker.equals("укр")) {
                    sawUkr = true;
                    ukrAdmitted++;
                }
            }

            require(sawUk && sawUkr,
                    "registry row must admit both ук and укр markers: " + id);
        }

        System.out.println(
                "(uk-surface-parity"
                        + " (schema " + (headerless ? "headerless" : "binary-8") + ")"
                        + " (rows " + rows + ")"
                        + " (ук-admitted " + ukAdmitted + ")"
                        + " (укр-admitted " + ukrAdmitted + ")"
                        + " (ук-absent " + ukAbsent + ")"
                        + " (укр-absent " + ukrAbsent + ")"
                        + " (roundtrip-checked " + roundtripChecked + ")"
                        + " (status pass))");
    }
}