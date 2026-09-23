package wsm.graalvm;

import java.util.List;
import java.util.Map;

/**
 * Mechanical projection from the Lisp-owned surface registry to exact Sid8.
 *
 * Surface spellings exist only on the reader side of this boundary. Every
 * admitted function identity stored or returned here is Sid8. Legacy decimal
 * IDs, quoted/string SID identities and name-based backend keys are rejected.
 */
public final class CanonRegistry {
    public record Row(Sid8 id, Map<String, String> surfaces) {}

    private final Map<Sid8, Row> rows = new java.util.TreeMap<>();
    private final Map<String, Sid8> spellingToId = new java.util.HashMap<>();

    public CanonRegistry() {}

    public CanonRegistry load(String registrySource) {
        List<Object> forms = new RegistrySexpReader(registrySource).readAll();
        if (forms.size() != 1 || !(forms.get(0) instanceof List<?> top) || top.isEmpty()) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "registry must contain exactly one top-level form");
        }

        Object header = top.get(0);
        if (isByteSidDescriptor(header)) {
            return loadHeaderlessByteSid(top, 1);
        }
        if (isByteSidRow(header)) {
            return loadHeaderlessByteSid(top, 0);
        }

        throw new WsmError(
                WsmError.Kind.PARSE,
                "registry must start with (binary 8) or a bare eight-bit SID row");
    }

    private static CanonRegistry loadHeaderlessByteSid(List<?> top, int startIndex) {
        CanonRegistry out = new CanonRegistry();

        for (int i = startIndex; i < top.size(); i++) {
            Object rowObject = top.get(i);
            if (!(rowObject instanceof List<?> row)
                    || row.isEmpty()
                    || !(row.get(0) instanceof String token)
                    || !token.matches("[01]{8}")) {
                throw new WsmError(
                        WsmError.Kind.PARSE,
                        "malformed bare-SID registry row");
            }

            Sid8 id = Sid8.parseBareToken(token);
            Map<String, String> faceMap = new java.util.LinkedHashMap<>();

            // The Java reader transports an unquoted source atom as String.
            // Normalize it here once; downstream identity is Sid8 only.
            putMapping(out.spellingToId, token, id);

            for (int j = 1; j < row.size(); j++) {
                Object surfaceObject = row.get(j);
                if (!(surfaceObject instanceof List<?> surface)
                        || surface.size() != 2
                        || !(surface.get(0) instanceof String marker)) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "malformed SID surface for " + id);
                }

                Object spellingObject = surface.get(1);
                if (spellingObject instanceof List<?> absent && absent.isEmpty()) {
                    continue;
                }
                if (!(spellingObject instanceof String spelling)) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "SID surface must be an atom/string or () for " + id);
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
                && row.get(0) instanceof String token
                && token.matches("[01]{8}");
    }

    private static void putRow(
            CanonRegistry out,
            Sid8 id,
            Map<String, String> faceMap) {
        if (out.rows.putIfAbsent(id, new Row(id, faceMap)) != null) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "duplicate SID: " + id);
        }
    }

    public static CanonRegistry registry(
            Map<Sid8, Row> rowsIn,
            Map<String, Sid8> spellIn) {
        CanonRegistry reg = new CanonRegistry();
        for (Map.Entry<Sid8, Row> e : rowsIn.entrySet()) {
            reg.rows.put(e.getKey(), e.getValue());
            putMapping(reg.spellingToId, e.getKey().toString(), e.getKey());
        }
        for (Map.Entry<String, Sid8> e : spellIn.entrySet()) {
            putMapping(reg.spellingToId, e.getKey(), e.getValue());
        }
        return reg;
    }

    public Row row(Sid8 id) {
        Row r = rows.get(id);
        if (r == null) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "unknown SID " + id);
        }
        return r;
    }

    /** Resolve one admitted source spelling or one exact bare SID token. */
    public Sid8 semanticIdForToken(String spelling) {
        return spellingToId.get(spelling);
    }

    public java.util.Set<Sid8> ids() {
        return java.util.Collections.unmodifiableSet(rows.keySet());
    }

    public boolean usesExactByteSids() {
        return true;
    }

    private static void putMapping(
            Map<String, Sid8> index,
            String spelling,
            Sid8 id) {
        Sid8 previous = index.putIfAbsent(spelling, id);
        if (previous != null && !previous.equals(id)) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "semantic registry surface collision: " + spelling
                            + " maps to both " + previous + " and " + id);
        }
    }
}
