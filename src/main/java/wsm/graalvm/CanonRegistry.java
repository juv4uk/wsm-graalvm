package wsm.graalvm;

import java.util.List;
import java.util.Map;

/**
 * Mechanical projection from the Lisp-owned semantic registry to numeric IDs.
 *
 * This class owns no language meaning. It only answers:
 * "which admitted semantic ID does this token spelling denote?"
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
                || top.isEmpty() || !"sr/1".equals(top.get(0))) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "registry must contain exactly one (sr/1 ...) form");
        }

        for (int i = 1; i < top.size(); i++) {
            Object rowObject = top.get(i);
            if (!(rowObject instanceof List<?> row)
                    || row.isEmpty()
                    || !(row.get(0) instanceof String id)
                    || !id.matches("\\d+")) {
                throw new WsmError(WsmError.Kind.PARSE, "malformed registry row");
            }

            Map<String, String> faceMap = new java.util.LinkedHashMap<>();

            // Opaque machine ID is itself an admitted runtime route.
            putMapping(out.spellingToId, id, id);

            for (int j = 1; j < row.size(); j++) {
                Object surfaceObject = row.get(j);
                if (!(surfaceObject instanceof List<?> surface)
                        || surface.size() < 3
                        || !(surface.get(0) instanceof String marker)
                        || !(surface.get(1) instanceof String spelling)
                        || !(surface.get(surface.size() - 1) instanceof String statusRaw)) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "malformed registry surface for " + id);
                }

                String status = statusRaw.toLowerCase();
                if ("—".equals(spelling) || !isAdmitted(status)) {
                    continue;
                }

                faceMap.put(marker, spelling);
                putMapping(out.spellingToId, spelling, id);
            }

            if (out.rows.putIfAbsent(id, new Row(id, faceMap)) != null) {
                throw new WsmError(
                        WsmError.Kind.PARSE,
                        "duplicate semantic id: " + id);
            }
        }

        return out;
    }

    public static CanonRegistry registry(
            Map<String, Row> rowsIn,
            Map<String, String> spellIn) {
        CanonRegistry reg = new CanonRegistry();
        for (Map.Entry<String, Row> e : rowsIn.entrySet()) {
            reg.rows.put(e.getKey(), e.getValue());
            putMapping(reg.spellingToId, e.getKey(), e.getKey());
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
                    "unknown semantic id " + id);
        }
        return r;
    }

    /**
     * Resolve an admitted language surface OR the numeric machine ID itself.
     * Returns null for ordinary user-level symbols.
     */
    public String semanticIdForToken(String spelling) {
        return spellingToId.get(spelling);
    }

    @Deprecated
    public String idForSpelling(String spelling) {
        return semanticIdForToken(spelling);
    }

    public java.util.Set<String> ids() {
        return java.util.Collections.unmodifiableSet(rows.keySet());
    }

    private static boolean isAdmitted(String status) {
        return status.equals("stable") || status.equals("compatibility-only");
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
