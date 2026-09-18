package wsm.graalvm;

/** M0 value space: NIL | Symbol | Long(exact integer) | Pair. Records are Pairs of Symbols. */
public final class Value {
    static final Object UNBOUND = new Object() {
        @Override public String toString() { return "#<unbound>"; }
    };

    public static final Object NIL = new Object() {
        @Override public String toString() { return "()"; }
    };

    public static final class Symbol {
        public final String name;
        Symbol(String name) { this.name = name; }
        @Override public String toString() { return name; }
        @Override public boolean equals(Object o) {
            return o instanceof Symbol s && s.name.equals(name);
        }
        @Override public int hashCode() { return name.hashCode(); }
    }

    public static final class Pair {
        public Object car;
        public Object cdr;
        public Pair(Object car, Object cdr) { this.car = car; this.cdr = cdr; }
    }

    /**
     * Language-owned callable identity. Equality is semantic-ID equality;
     * Java object allocation identity is irrelevant.
     */
    public record SemanticRef(String id) {
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

    public static Pair list(java.util.List<Object> items) {
        Pair head = null, tail = null;
        for (Object item : items) {
            Pair cell = new Pair(item, NIL);
            if (head == null) head = cell; else tail.cdr = cell;
            tail = cell;
        }
        return head;
    }

    public static Object record(String kind, String state) {
        return list(java.util.List.of(symbol(kind), symbol(state)));
    }

    /** Rust parity: is_atom = not a Pair; NIL is an atom for every observable purpose. */
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
