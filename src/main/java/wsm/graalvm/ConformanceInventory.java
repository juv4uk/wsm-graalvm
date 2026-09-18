package wsm.graalvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw implementation-independent conformance corpus inventory.
 *
 * Derived from the shared #36 gate. This class reads published fixture facts
 * and does not decide whether historical fixtures are current authority.
 */
public final class ConformanceInventory {
    public record Fixture(
            String id,
            String expr,
            String expected,
            String error,
            String role,
            List<String> requires,
            List<Long> sinceContract) {}

    private ConformanceInventory() {}

    public static List<Fixture> selectTier(String source, long tier) {
        List<Fixture> selected = new ArrayList<>();
        for (Object form : new Reader(source).readAll()) {
            Map<String, Object> fields = alist(form);
            Object fixtureTier = fields.get("tier");
            if (!(fixtureTier instanceof Value.NumberValue n)
                    || !n.denominator().equals(java.math.BigInteger.ONE)) {
                throw invalid("fixture tier must be an integer");
            }
            if (!n.numerator().equals(java.math.BigInteger.valueOf(tier))) {
                continue;
            }

            String expr = requiredString(fields, "expr");
            String expected = optionalString(fields, "expected");
            String error = optionalString(fields, "error");
            if ((expected == null) == (error == null)) {
                throw invalid("fixture must contain exactly one of expected/error: " + expr);
            }

            selected.add(new Fixture(
                    "F%02d".formatted(selected.size() + 1),
                    expr,
                    expected,
                    error,
                    optionalString(fields, "role"),
                    symbolList(fields.get("requires"), "requires"),
                    integerList(fields.get("since-contract"), "since-contract")));
        }
        return List.copyOf(selected);
    }

    private static Map<String, Object> alist(Object value) {
        Map<String, Object> fields = new LinkedHashMap<>();
        Object cursor = value;
        while (cursor instanceof Value.Pair cell) {
            if (!(cell.car instanceof Value.Pair entry)) {
                throw invalid("fixture entry must be a dotted pair");
            }
            String key = token(entry.car, "fixture key");
            if (fields.putIfAbsent(key, entry.cdr) != null) {
                throw invalid("duplicate fixture key: " + key);
            }
            cursor = cell.cdr;
        }
        if (cursor != Value.NIL) throw invalid("fixture must be a proper alist");
        return fields;
    }

    private static String requiredString(Map<String, Object> fields, String key) {
        String value = optionalString(fields, key);
        if (value == null) throw invalid("fixture missing " + key);
        return value;
    }

    private static String optionalString(Map<String, Object> fields, String key) {
        Object value = fields.get(key);
        if (value == null) return null;
        if (value instanceof Value.StringValue text) return text.value;
        throw invalid(key + " must be a string");
    }

    private static List<String> symbolList(Object value, String field) {
        if (value == null) return List.of();
        List<String> out = new ArrayList<>();
        Object cursor = value;
        while (cursor instanceof Value.Pair cell) {
            out.add(token(cell.car, field + " item"));
            cursor = cell.cdr;
        }
        if (cursor != Value.NIL) throw invalid(field + " must be a proper symbol list");
        return out;
    }

    private static List<Long> integerList(Object value, String field) {
        if (value == null) return List.of();
        List<Long> out = new ArrayList<>();
        Object cursor = value;
        while (cursor instanceof Value.Pair cell) {
            if (!(cell.car instanceof Value.NumberValue n)
                    || !n.denominator().equals(java.math.BigInteger.ONE)
                    || !n.numerator().bitLengthIsLessThan(63)) {
                throw invalid(field + " must be a bounded integer list");
            }
            out.add(n.numerator().longValue());
            cursor = cell.cdr;
        }
        if (cursor != Value.NIL) throw invalid(field + " must be a proper integer list");
        return out;
    }

    private static String token(Object value, String context) {
        if (value instanceof Reader.Token t) return t.spelling();
        if (value instanceof Value.Symbol s) return s.name;
        throw invalid(context + " must be a symbol");
    }

    private static WsmError invalid(String detail) {
        return new WsmError(WsmError.Kind.INVALID_FORM, detail);
    }
}
