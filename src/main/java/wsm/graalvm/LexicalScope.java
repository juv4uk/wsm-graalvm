package wsm.graalvm;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compile-time lexical layout only. Never stores runtime Lisp values.
 */
final class LexicalScope {
    record Binding(int depth, int slot) {}

    private final LexicalScope parent;
    private final GlobalBindings globals;
    private final boolean root;
    private final FrameDescriptor.Builder builder;
    private final Map<String, Integer> locals = new LinkedHashMap<>();
    private final int captureFlagSlot;
    private final int tailArgsSlot;
    private final int tailResultSlot;
    private boolean finished;

    private LexicalScope(
            LexicalScope parent,
            GlobalBindings globals,
            boolean root) {
        this.parent = parent;
        this.globals = globals;
        this.root = root;
        this.builder = root
                ? null
                : FrameDescriptor.newBuilder().defaultValue(Value.UNBOUND);
        if (root) {
            this.captureFlagSlot = -1;
            this.tailArgsSlot = -1;
            this.tailResultSlot = -1;
        } else {
            this.captureFlagSlot = builder.addSlot(
                    FrameSlotKind.Object, new Object(), null);
            this.tailArgsSlot = builder.addSlot(
                    FrameSlotKind.Object, new Object(), null);
            this.tailResultSlot = builder.addSlot(
                    FrameSlotKind.Object, new Object(), null);
        }
    }

    static LexicalScope root(GlobalBindings globals) {
        return new LexicalScope(null, globals, true);
    }

    LexicalScope child() {
        return new LexicalScope(this, globals, false);
    }

    boolean isRoot() {
        return root;
    }

    GlobalBindings globals() {
        return globals;
    }

    int captureFlagSlot() {
        if (root) throw new IllegalStateException("root scope has no frame slots");
        return captureFlagSlot;
    }

    int tailArgsSlot() {
        if (root) throw new IllegalStateException("root scope has no frame slots");
        return tailArgsSlot;
    }

    int tailResultSlot() {
        if (root) throw new IllegalStateException("root scope has no frame slots");
        return tailResultSlot;
    }

    int declareLocal(String name) {
        if (root) {
            throw new IllegalStateException("root declarations belong to GlobalBindings");
        }
        Integer existing = locals.get(name);
        if (existing != null) return existing;
        if (finished) {
            throw new IllegalStateException("frame layout already finalized");
        }
        int slot = builder.addSlot(FrameSlotKind.Object, name, null);
        locals.put(name, slot);
        return slot;
    }

    Binding resolveLocal(String name) {
        int depth = 0;
        LexicalScope scope = this;
        while (scope != null && !scope.root) {
            Integer slot = scope.locals.get(name);
            if (slot != null) {
                return new Binding(depth, slot);
            }
            scope = scope.parent;
            depth++;
        }
        return null;
    }

    FrameDescriptor finishFrame() {
        if (root) {
            throw new IllegalStateException("root scope has no lexical frame");
        }
        if (finished) {
            throw new IllegalStateException("frame layout already finalized");
        }
        finished = true;
        return builder.build();
    }
}
