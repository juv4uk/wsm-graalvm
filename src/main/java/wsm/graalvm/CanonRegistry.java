package wsm.graalvm;

import java.util.List;
import java.util.Map;

/**
 * Mechanical projection from the Lisp-owned semantic registry to semantic IDs.
 *
 * This class owns no language meaning. It only answers:
 * "which admitted semantic ID does this token spelling denote?"
 *
 * Three source schemas are accepted during the #230 cutover:
 * - legacy (sr/1 ...) rows from the currently pinned v0.1 baseline;
 * - explicit ((binary 8) ...) rows whose exact eight-bit spelling IS identity;
 * - headerless (...) rows starting directly with an eight-bit SID row.
 *
 * There is intentionally no legacy-ID -> byte-SID translation table here.
 */
public final class CanonRegistry {
    public record Row(String id, Map<String, String> surfaces) {}

    private final Map<String, Row> rows = new java.util.TreeMap<>();
    private final Map<String, String> spellingToId = new java.util.HashMap<>();
    private boolean legacyNumericIds;

    public CanonRegistry() {}

    public CanonRegistry load(String registrySource) {
        List<Object> forms = new RegistrySexpReader(registrySource).readAll();
        if (forms.size() != 1 || !(forms.get(0) instanceof List<?> top) || top.isEmpty()) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "registry must contain exactly one top-level form");
        }

        Object header = top.get(0);
        if (header instanceof String marker && "sr/1".equals(marker)) {
            return loadLegacy(top);
        }
        if (isByteSidDescriptor(header)) {
            return loadByteSid(top);
        }
        if (isByteSidRow(header)) {
            return loadHeaderlessByteSid(top);
        }

        throw new WsmError(
                WsmError.Kind.PARSE,
                "registry must start with sr/1, (binary 8), or a byte-SID row");
    }

    private static CanonRegistry loadLegacy(List<?> top) {
        CanonRegistry out = new CanonRegistry();
        out.legacyNumericIds = true;

        for (int i = 1; i < top.size(); i++) {
            Object rowObject = top.get(i);
            if (!(rowObject instanceof List<?> row)
                    || row.isEmpty()
                    || !(row.get(0) instanceof String id)
                    || !id.matches("\\d+")) {
                throw new WsmError(WsmError.Kind.PARSE, "malformed legacy registry row");
            }

            Map<String, String> faceMap = new java.util.LinkedHashMap<>();
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
                            "malformed legacy registry surface for " + id);
                }

                String status = statusRaw.toLowerCase();
                if ("—".equals(spelling) || !isAdmitted(status)) {
                    continue;
                }

                faceMap.put(marker, spelling);
                putMapping(out.spellingToId, spelling, id);
            }

            putRow(out, id, faceMap);
        }
        return out;
    }

    private static CanonRegistry loadByteSid(List<?> top) {
        return loadHeaderlessByteSid(top, 1);
    }

    private static CanonRegistry loadHeaderlessByteSid(List<?> top) {
        return loadHeaderlessByteSid(top, 0);
    }

    private static CanonRegistry loadHeaderlessByteSid(List<?> top, int startIndex) {
        CanonRegistry out = new CanonRegistry();
        out.legacyNumericIds = false;

        for (int i = startIndex; i < top.size(); i++) {
            Object rowObject = top.get(i);
            if (!(rowObject instanceof List<?> row)
                    || row.isEmpty()
                    || !(row.get(0) instanceof String id)
                    || !id.matches("[01]{8}")) {
                throw new WsmError(
                        WsmError.Kind.PARSE,
                        "malformed byte-SID registry row");
            }

            Map<String, String> faceMap = new java.util.LinkedHashMap<>();
            putMapping(out.spellingToId, id, id);

            for (int j = 1; j < row.size(); j++) {
                Object surfaceObject = row.get(j);
                if (!(surfaceObject instanceof List<?> surface)
                        || surface.size() != 2
                        || !(surface.get(0) instanceof String marker)) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "malformed byte-SID registry surface for " + id);
                }

                Object spellingObject = surface.get(1);
                if (spellingObject instanceof List<?> absent && absent.isEmpty()) {
                    continue;
                }
                if (!(spellingObject instanceof String spelling)) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "byte-SID surface must be an atom/string or () for " + id);
                }

                faceMap.put(marker, spelling);
                putMapping(out.spellingToId, spelling, id);
            }

            putRow(out, id, faceMap);
        }
        return out;
    }

    private static boolean isByteSidDescriptor(Object form) {
        return form instanceof List<?> descriptor
                && descriptor.size() == 2
                && "binary".equals(descriptor.get(0))
                && "8".equals(descriptor.get(1));
    }

    private static boolean isByteSidRow(Object form) {
        return form instanceof List<?> row
                && !row.isEmpty()
                && row.get(0) instanceof String id
                && id.matches("[01]{8}");
    }

    private static void putRow(
            CanonRegistry out,
            String id,
            Map<String, String> faceMap) {
        if (out.rows.putIfAbsent(id, new Row(id, faceMap)) != null) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "duplicate semantic id: " + id);
        }
    }

    public static CanonRegistry registry(
            Map<String, Row> rowsIn,
            Map<String, String> spellIn) {
        CanonRegistry reg = new CanonRegistry();
        reg.legacyNumericIds =
                !rowsIn.isEmpty()
                        && rowsIn.keySet().stream().allMatch(id -> id.matches("\\d{4}"));

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
     * Resolve an admitted language surface OR the exact machine ID itself.
     * Returns null for ordinary user-level symbols.
     */
    public String semanticIdForToken(String spelling) {
        return spellingToId.get(spelling);
    }

    /**
     * Legacy issue #47 bridge only.
     *
     * Current exact eight-bit SIDs contain leading zeroes and remain Reader
     * tokens, so they must never be reconstructed from a decimal numeric value.
     */
    public String semanticIdForNumeric(long value) {
        if (!legacyNumericIds) return null;
        String id = String.format("%04d", value);
        return rows.containsKey(id) ? id : null;
    }

    @Deprecated
    public String idForSpelling(String spelling) {
        return semanticIdForToken(spelling);
    }

    public java.util.Set<String> ids() {
        return java.util.Collections.unmodifiableSet(rows.keySet());
    }

    public boolean usesExactByteSids() {
        return !legacyNumericIds;
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
