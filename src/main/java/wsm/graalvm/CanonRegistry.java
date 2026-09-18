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
        List<Object> forms = new Reader(registrySource).readAll();
        if (forms.isEmpty()) throw new WsmError(WsmError.Kind.PARSE, "registry empty");
        Object form = forms.get(0);
        if (!(form instanceof Value.Pair head)
                || !(head.car instanceof Token t) || !t.spelling().equals("sr/1"))
            throw new WsmError(WsmError.Kind.PARSE, "registry must start with (sr/1 ...)");
        for (Object rowO : rows(head.cdr)) {
            Value.Pair row = (Value.Pair) rowO;
            String id = ((Token) row.car).spelling();
            Map<String, String> faceMap = new java.util.LinkedHashMap<>();
            for (Object surfaceO : rows(row.cdr)) {
                Value.Pair surface = (Value.Pair) surfaceO;
                List<Object> fields = cellList(surface);
                // surface shape: (marker spelling status) — status filter:
                // only "stable" is machinery-resting surface here.
                String status = ((Token) fields.get(fields.size() - 1)).spelling().toLowerCase();
                if (status.equals("stable") && fields.size() >= 2) {
                    String marker = ((Token) fields.get(0)).spelling();
                    String spelling = ((Token) fields.get(1)).spelling();
                    faceMap.put(marker, spelling);
                    this.spellingToId.put(spelling, id);
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
