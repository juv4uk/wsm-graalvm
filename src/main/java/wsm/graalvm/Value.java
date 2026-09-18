package wsm.graalvm;

/** Runtime value space owned by the Graal substrate mechanism. */
public final class Value {
    public static final Object NIL = new TruffleNil();

    /** Internal frame sentinel; never a Lisp-visible value. */
    public static final Object UNBOUND = new Object() {
        @Override public String toString() { return "#<unbound>"; }
    };

    /** NIL is its own interop value; identity is nil == nil everywhere. */
    public static final class TruffleNil implements com.oracle.truffle.api.interop.TruffleObject {
        private TruffleNil() {}
        @Override public String toString() { return "()"; }
    }

    public static final class Symbol implements com.oracle.truffle.api.interop.TruffleObject {
        public final String name;
        Symbol(String name) { this.name = name; }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) {
            return o instanceof Symbol s && s.name.equals(name);
        }
        @Override public int hashCode() { return name.hashCode(); }
    }

    public static final class Pair implements com.oracle.truffle.api.interop.TruffleObject {
        public Object car;
        public Object cdr;
        public Pair(Object car, Object cdr) { this.car = car; this.cdr = cdr; }
    }

    /** Language-owned callable identity; Java allocation identity is irrelevant. */
    public record SemanticRef(String id) implements com.oracle.truffle.api.interop.TruffleObject {
        public SemanticRef {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("semantic id must be non-empty");
            }
        }
    }

    private static final java.util.Map<String, Symbol> INTERN = new java.util.HashMap<>();

    public static Symbol symbol(String name) {
        return INTERN.computeIfAbsent(name, Symbol::new);
    }

    public static Pair pair(Object car, Object cdr) {
        return new Pair(car, cdr);
    }

    public static Object list(java.util.List<Object> items) {
        Object result = NIL;
        for (int i = items.size() - 1; i >= 0; i--) {
            result = new Pair(items.get(i), result);
        }
        return result;
    }

    public static Object record(String kind, String state) {
        return list(java.util.List.of(symbol(kind), symbol(state)));
    }

    public static boolean isAtom(Object v) {
        return !(v instanceof Pair);
    }

    static final Symbol SYM_IDENTITY_RELATION = symbol("identity-relation");
    static final Symbol SYM_SAME = symbol("same");

    public static Object identitySame() {
        return record("identity-relation", "same");
    }

    public static Object structuralKind(Object v) {
        if (v == NIL) return record("structural-kind", "empty-list");
        if (v instanceof Pair) return record("structural-kind", "pair");
        return record("structural-kind", "atom");
    }
}
