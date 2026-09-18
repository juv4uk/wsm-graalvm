package wsm.graalvm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw implementation-independent conformance corpus inventory.
 *
 * This class reads published fixture facts and tags. It deliberately does not
 * decide whether a historical fixture is current semantic authority; upstream
 * my-lisp owns that policy.
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
            if (!(fixtureTier instanceof Long n)) {
                throw invalid("fixture tier must be an integer");
            }
            if (n != tier) continue;

            String expr = requiredString(fields, "expr");
            String expected = optionalString(fields, "expected");
            String error = optionalString(fields, "error");

            if ((expected == null) == (error == null)) {
                throw invalid("fixture must contain exactly one of expected/error: " + expr);
            }

            String role = optionalString(fields, "role");
            List<String> requires = symbolList(fields.get("requires"), "requires");
            List<Long> sinceContract = integerList(fields.get("since-contract"), "since-contract");

            selected.add(new Fixture(
                    "F%02d".formatted(selected.size() + 1),
                    expr,
                    expected,
                    error,
                    role,
                    List.copyOf(requires),
                    List.copyOf(sinceContract)));
        }

        return List.copyOf(selected);
    }

    public static String emitLisp(List<Fixture> fixtures, long tier) {
        StringBuilder out = new StringBuilder();

        for (Fixture fixture : fixtures) {
            out.append("((id . ").append(fixture.id()).append(')')
                    .append(" (expr . ").append(quoted(fixture.expr())).append(')')
                    .append(" (expected . ").append(optionalQuoted(fixture.expected())).append(')')
                    .append(" (error . ").append(optionalQuoted(fixture.error())).append(')')
                    .append(" (role . ").append(optionalQuoted(fixture.role())).append(')')
                    .append(" (requires . ").append(symbolListSource(fixture.requires())).append(')')
                    .append(" (since-contract . ").append(integerListSource(fixture.sinceContract())).append("))")
                    .append('\n');
        }

        out.append("((summary . tier-inventory)")
                .append(" (tier . ").append(tier).append(')')
                .append(" (selected . ").append(fixtures.size()).append("))")
                .append('\n');

        return out.toString();
    }

    private static String optionalQuoted(String value) {
        return value == null ? "()" : quoted(value);
    }

    private static String quoted(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static String symbolListSource(List<String> values) {
        return "(" + String.join(" ", values) + ")";
    }

    private static String integerListSource(List<Long> values) {
        StringBuilder out = new StringBuilder("(");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(' ');
            out.append(values.get(i));
        }
        return out.append(')').toString();
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

        if (cursor != Value.NIL) {
            throw invalid("fixture must be a proper alist");
        }
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
        if (value instanceof Value.Str s) return s.value;
        if (value instanceof String s) return s;
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
            if (!(cell.car instanceof Long n)) {
                throw invalid(field + " must be an integer list");
            }
            out.add(n);
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
