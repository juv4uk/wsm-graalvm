package wsm.graalvm;

import wsm.graalvm.Reader.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Registry consumption keyed ONLY by numeric semantic identity.
 * Surfaces are data: the map keeps every stable surface spelling of an id,
 * and code never mentions words — only ids ("0004"), never "cons"/"car".
 */
public final class CanonRegistry {
    public record Row(String id, Map<String, String> surfaces) {}

    private final Map<String, Row> rows = new java.util.TreeMap<>();
    private final Map<String, String> spellingToId = new java.util.HashMap<>();

    public CanonRegistry() {}

    public CanonRegistry load(String registrySource) {
        CanonRegistry reg = new CanonRegistry();
        List<Object> forms = new Reader(registrySource, true).readAll();
        if (forms.isEmpty()) throw new WsmError(WsmError.Kind.PARSE, "registry empty");
        Object form = forms.get(0);
        if (form == null) throw new WsmError(WsmError.Kind.PARSE, "registry empty (no forms)");
        if (!(form instanceof Value.Pair head)
                || !(head.car instanceof Token t) || !t.spelling().equals("sr/1"))
            throw new WsmError(WsmError.Kind.PARSE, "registry must start with (sr/1 ...), got first-form=" + form);
        for (Object rowO : rows(head.cdr)) {
            Value.Pair row = (Value.Pair) rowO;
            String id = ((Token) row.car).spelling();
            Map<String, String> faceMap = new java.util.LinkedHashMap<>();
            putMapping(this.spellingToId, id, id);
            for (Object surfaceO : rows(row.cdr)) {
                Value.Pair surface = (Value.Pair) surfaceO;
                List<Object> fields = cellList(surface);                // surface shape: (marker spelling status) — status filter:
                // only "stable" is machinery-resting surface here.
                // Special case (issue #8): the sym surface `'` is APOSTROPHE
                // sugar; as reader data it desugars into (QUOTE_HEAD spelling).
                if (fields.size() == 2
                        && fields.get(0) instanceof Token markerToken
                        && fields.get(1) instanceof Value.Pair ap
                        && ap.car == Reader.QUOTE_HEAD
                        && ap.cdr instanceof Token statusToken) {
                    if (statusToken.spelling().toLowerCase().equals("stable")) {
                        faceMap.put(markerToken.spelling(), "'");
                        this.spellingToId.put("'", id);
                    }
                    continue;
                }
                String status = ((Token) fields.get(fields.size() - 1)).spelling().toLowerCase();
                if (status.equals("stable") && fields.size() >= 2) {
                    String marker = ((Token) fields.get(0)).spelling();
                    String spelling = ((Token) fields.get(1)).spelling();
                    faceMap.put(marker, spelling);
                    this.spellingToId.put(spelling, id);
                }
                // compatibility-only surfaces register as secondary routes
                // (e.g. id 1000 `def` -> canonical 0011); they can never
                // replace a stable spelling of the same identity.
                if (status.equals("compatibility-only") && fields.size() >= 2
                        && fields.get(0) instanceof Token cMarker
                        && fields.get(1) instanceof Token cSpelling) {
                    this.spellingToId.putIfAbsent(cSpelling.spelling(), id);
                }
            }
            this.rows.put(id, new Row(id, faceMap));
        }
        return this;
    }

    public static CanonRegistry registry(Map<String, Row> rowsIn, Map<String, String> spellIn) {
        CanonRegistry reg = new CanonRegistry();
        for (Map.Entry<String, Row> e : rowsIn.entrySet()) reg.rows.put(e.getKey(), e.getValue());
        reg.spellingToId.putAll(spellIn);
        return reg;
    }

    public Row row(String id) {
        Row r = rows.get(id);
        if (r == null) throw new WsmError(WsmError.Kind.INVALID_FORM, "unknown semantic id " + id);
        return r;
    }

    public String idForSpelling(String spelling) { return spellingToId.get(spelling); }

    private static void putMapping(Map<String, String> index, String spelling, String id) {
        String previous = index.putIfAbsent(spelling, id);
        if (previous != null && !previous.equals(id)) {
            throw new WsmError(WsmError.Kind.PARSE,
                    "semantic surface collision: " + spelling + " -> " + previous + " / " + id);
        }
    }

    private static void putMappingIfAbsent(Map<String, String> index, String spelling, String id) {
        index.putIfAbsent(spelling, id);
    }

    public java.util.Set<String> ids() { return rows.keySet(); }

    private static Iterable<Object> rows(Object list) {
        List<Object> out = new ArrayList<>();
        Object cur = list;
        while (cur instanceof Value.Pair p) { out.add(p.car); cur = p.cdr; }
        return out;
    }

    private static List<Object> cellList(Object list) {
        List<Object> out = new ArrayList<>();
        Object cur = list;
        while (cur instanceof Value.Pair p) { out.add(p.car); cur = p.cdr; }
        if (cur != Value.NIL)
            throw new WsmError(WsmError.Kind.PARSE, "improper request in registry row");
        return out;
    }
}
