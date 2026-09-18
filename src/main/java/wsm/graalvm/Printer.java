package wsm.graalvm;

public final class Printer {
    private Printer() {}

    public static String printer(Object v) { return print(v); }

    public static String print(Object v) {
        if (v == Value.NIL) return "()";
        if (v instanceof Value.Symbol s) return s.name;
        if (v instanceof Value.StringValue s) return '"' + escape(s.value) + '"';
        if (v instanceof Value.NumberValue n) return n.toString();
        if (v instanceof Value.SemanticRef semantic) {
            return "#<semantic-ref " + semantic.id() + ">";
        }
        if (v instanceof Value.Pair p) {
            StringBuilder sb = new StringBuilder("(");
            sb.append(print(p.car));
            Object rest = p.cdr;
            while (rest instanceof Value.Pair pr) {
                sb.append(' ').append(print(pr.car));
                rest = pr.cdr;
            }
            if (rest != Value.NIL) sb.append(" . ").append(print(rest));
            sb.append(')');
            return sb.toString();
        }
        return String.valueOf(v);
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '"' -> out.append('\\').append('"');
                case '\\' -> out.append('\\').append('\\');
                default -> out.append(value.charAt(i));
            }
        }
        return out.toString();
    }
}
