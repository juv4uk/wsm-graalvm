package wsm.graalvm;

import java.util.ArrayList;
import java.util.List;

/** Environment: single shared lexical frame per definition scope chain (my-lisp semantics). */
public final class Environment {
    private final Environment parent;
    private final java.util.HashMap<String, Object> slots = new java.util.HashMap<>();

    public Environment(Environment parent) { this.parent = parent; }

    public void define(String name, Object value) {
        slots.put(name, value);
    }

    public Object lookup(String name) {
        Environment e = this;
        while (e != null) {
            Object v = e.slots.get(name);
            if (v != null || e.slots.containsKey(name)) return v;
            e = e.parent;
        }
        throw new WsmError(WsmError.Kind.TYPE, "unbound symbol: " + name);
    }

    public boolean lookupOrNull(String name, Object[] out) {
        Environment e = this;
        while (e != null) {
            if (e.slots.containsKey(name)) { e.slots.get(name); out[0] = e.slots.get(name); return true; }
            e = e.parent;
        }
        return false;
    }
}
