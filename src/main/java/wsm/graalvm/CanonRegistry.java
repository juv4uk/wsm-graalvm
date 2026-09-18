package wsm.graalvm;

import wsm.graalvm.Reader.Token;
import java.util.ArrayList;
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
        List<Object> forms = new Reader(registrySource).readAll();
        if (forms.isEmpty()) throw new WsmError(WsmError.Kind.PARSE, "registry empty");

        Object form = forms.get(0);
        if (!(form instanceof Value.Pair head)
                || !(head.car instanceof Token t) || !t.spelling().equals("sr/1")) {
            throw new WsmError(WsmError.Kind.PARSE, "registry must start with (sr/1 ...)");
        }

        for (Object rowO : rows(head.cdr)) {
            if (!(rowO instanceof Value.Pair row)
                    || !(row.car instanceof Token idToken)) {
                throw new WsmError(WsmError.Kind.PARSE, "malformed registry row");
            }

            String id = idToken.spelling();
            Map<String, String> faceMap = new java.util.LinkedHashMap<>();

            // The opaque numeric machine ID is itself an admitted runtime route.
            putMapping(out.spellingToId, id, id);

            for (Object surfaceO : rows(row.cdr)) {
                if (!(surfaceO instanceof Value.Pair surface)) {
                    throw new WsmError(WsmError.Kind.PARSE, "malformed registry surface");
                }

                List<Object> fields = cellList(surface);
                if (fields.size() < 3
                        || !(fields.get(0) instanceof Token markerToken)
                        || !(fields.get(1) instanceof Token spellingToken)
                        || !(fields.get(fields.size() - 1) instanceof Token statusToken)) {
                    throw new WsmError(WsmError.Kind.PARSE, "malformed registry surface fields");
                }

                String marker = markerToken.spelling();
                String spelling = spellingToken.spelling();
                String status = statusToken.spelling().toLowerCase();

                if (spelling.equals("—")) continue;
                if (!isAdmitted(status)) continue;

                faceMap.put(marker, spelling);
                putMapping(out.spellingToId, spelling, id);
            }

            out.rows.put(id, new Row(id, faceMap));
        }

        return out;
    }

    public static CanonRegistry registry(Map<String, Row> rowsIn, Map<String, String> spellIn) {
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
        if (r == null) throw new WsmError(WsmError.Kind.INVALID_FORM, "unknown semantic id " + id);
        return r;
    }

    /**
     * Resolve an admitted language surface OR the numeric machine ID itself.
     * Returns null for ordinary user-level symbols.
     */
    public String semanticIdForToken(String spelling) {
        return spellingToId.get(spelling);
    }

    /** Compatibility name for older M0 callers. */
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

    private static void putMapping(Map<String, String> index, String spelling, String id) {
        String previous = index.putIfAbsent(spelling, id);
        if (previous != null && !previous.equals(id)) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "semantic registry surface collision: " + spelling
                            + " maps to both " + previous + " and " + id);
        }
    }

    private static Iterable<Object> rows(Object list) {
        List<Object> out = new ArrayList<>();
        Object cur = list;
        while (cur instanceof Value.Pair p) {
            out.add(p.car);
            cur = p.cdr;
        }
        if (cur != Value.NIL) {
            throw new WsmError(WsmError.Kind.PARSE, "improper registry list");
        }
        return out;
    }

    private static List<Object> cellList(Object list) {
        List<Object> out = new ArrayList<>();
        Object cur = list;
        while (cur instanceof Value.Pair p) {
            out.add(p.car);
            cur = p.cdr;
        }
        if (cur != Value.NIL) {
            throw new WsmError(WsmError.Kind.PARSE, "improper request in registry row");
        }
        return out;
    }
}
