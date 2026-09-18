package wsm.graalvm;

public final class Printer {
    public static String printer(Object v) { return print(v); }

    public static String print(Object v) {
        if (v == Value.NIL) return "()";
        if (v instanceof Value.Symbol s) return s.name;
        if (v instanceof Value.Str st) return '"' + st.value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        if (v instanceof Long l) return Long.toString(l);
        if (v instanceof Value.Pair p) {
            StringBuilder sb = new StringBuilder("(");
            if (p.car instanceof Value.Symbol marker && marker.name.equals("0001")
                    && p.cdr instanceof Value.Pair) {
                sb.append("'");
            }
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
}
