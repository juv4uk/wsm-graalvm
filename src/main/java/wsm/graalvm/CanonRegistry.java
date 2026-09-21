package wsm.graalvm;

import java.util.List;
import java.util.Map;

/**
 * Mechanical projection from the Lisp-owned semantic registry to exact 8-bit SIDs.
 *
 * This class owns no language meaning. It only answers:
 * "which admitted semantic SID does this token spelling denote?"
 */
public final class CanonRegistry {
    public record Row(String id, Map<String, String> surfaces) {}

    private final Map<String, Row> rows = new java.util.TreeMap<>();
    private final Map<String, String> spellingToId = new java.util.HashMap<>();

    public CanonRegistry() {}

    public CanonRegistry load(String registrySource) {
        CanonRegistry out = new CanonRegistry();
        List<Object> forms = new RegistrySexpReader(registrySource).readAll();
        if (forms.size() != 1 || !(forms.get(0) instanceof List<?> top)
                || top.size() < 2 || !isBinary8Header(top.get(0))) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "registry must contain exactly one ((binary 8) <8-bit rows...>) form");
        }

        for (int i = 1; i < top.size(); i++) {
            Object rowObject = top.get(i);
            if (!(rowObject instanceof List<?> row)
                    || row.isEmpty()
                    || !(row.get(0) instanceof String id)
                    || !id.matches("[01]{8}")) {
                throw new WsmError(WsmError.Kind.PARSE, "malformed 8-bit registry row");
            }

            Map<String, String> faceMap = new java.util.LinkedHashMap<>();

            // Exact bit spelling is itself the semantic identity route.
            putMapping(out.spellingToId, id, id);

            for (int j = 1; j < row.size(); j++) {
                Object surfaceObject = row.get(j);
                if (!(surfaceObject instanceof List<?> surface)
                        || surface.size() != 2
                        || !(surface.get(0) instanceof String marker)) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "malformed registry surface for " + id);
                }

                String spelling = surfaceSpelling(surface.get(1), id, marker);
                if (spelling == null) {
                    continue;
                }

                faceMap.put(marker, spelling);
                putMapping(out.spellingToId, spelling, id);
            }

            if (out.rows.putIfAbsent(id, new Row(id, faceMap)) != null) {
                throw new WsmError(
                        WsmError.Kind.PARSE,
                        "duplicate semantic SID: " + id);
            }
        }

        return out;
    }

    public static CanonRegistry registry(
            Map<String, Row> rowsIn,
            Map<String, String> spellIn) {
        CanonRegistry reg = new CanonRegistry();
        for (Map.Entry<String, Row> e : rowsIn.entrySet()) {
            String id = e.getKey();
            if (!id.matches("[01]{8}")) {
                throw new IllegalArgumentException("semantic SID must be exactly 8 bits: " + id);
            }
            reg.rows.put(id, e.getValue());
            putMapping(reg.spellingToId, id, id);
        }
        for (Map.Entry<String, String> e : spellIn.entrySet()) {
            putMapping(reg.spellingToId, e.getKey(), e.getValue());
        }
        return reg;
    }

    public Row row(String id) {
        Row r = rows.get(id);
        if (r == null) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "unknown semantic SID " + id);
        }
        return r;
    }

    /**
     * Resolve an admitted language surface OR the exact 8-bit SID itself.
     * Returns null for ordinary user-level symbols and historical short IDs.
     */
    public String semanticIdForToken(String spelling) {
        return spellingToId.get(spelling);
    }

    public java.util.Set<String> ids() {
        return java.util.Collections.unmodifiableSet(rows.keySet());
    }

    private static boolean isBinary8Header(Object value) {
        if (!(value instanceof List<?> header) || header.size() != 2) return false;
        return "binary".equals(header.get(0)) && "8".equals(header.get(1));
    }

    private static String surfaceSpelling(
            Object raw,
            String id,
            String marker) {
        if (raw instanceof String spelling) {
            if (spelling.isBlank()) {
                throw new WsmError(
                        WsmError.Kind.PARSE,
                        "blank registry surface for " + id + " " + marker);
            }
            return spelling;
        }
        if (raw instanceof List<?> missing && missing.isEmpty()) {
            return null;
        }
        throw new WsmError(
                WsmError.Kind.PARSE,
                "registry surface must be spelling or () for " + id + " " + marker);
    }

    private static void putMapping(
            Map<String, String> index,
            String spelling,
            String id) {
        String previous = index.putIfAbsent(spelling, id);
        if (previous != null && !previous.equals(id)) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "semantic registry surface collision: " + spelling
                            + " maps to both " + previous + " and " + id);
        }
    }
}
