package wsm.graalvm;

import com.oracle.truffle.api.interop.TruffleObject;

/**
 * First-class macro value produced by the narrow upstream make-macro host
 * mechanism. Macro behavior itself remains in Lisp (lib/macro.lisp).
 */
final class MacroValue implements WsmFunc, TruffleObject {
    private final Closure closure;

    MacroValue(Closure closure) {
        this.closure = closure;
    }

    @Override
    public Object call(Object[] args) {
        return closure.call(args);
    }

    Closure closure() {
        return closure;
    }
}
