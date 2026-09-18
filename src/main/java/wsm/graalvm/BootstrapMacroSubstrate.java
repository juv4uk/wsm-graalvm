package wsm.graalvm;

import com.oracle.truffle.api.interop.TruffleObject;

/**
 * The single narrow bootstrap mechanism required by pinned my-lisp before
 * evaluating lib/macro.lisp: materialize an existing Closure as a Macro value.
 *
 * This is mechanism, not meaning. Public macro-definition identity 0012 remains
 * owned by the upstream semantic registry and the Lisp-defined macro layer.
 */
final class BootstrapMacroSubstrate {
    private static final String BINDING = "make-macro";

    private BootstrapMacroSubstrate() {}

    static void install(GlobalBindings globals) {
        globals.define(BINDING, BootstrapMacroSubstrate::invoke);
    }

    static Object invoke(Object[] args) {
        WsmError.arity(args, 1, BINDING);
        if (!(args[0] instanceof Closure closure)) {
            throw new WsmError(
                    WsmError.Kind.TYPE,
                    BINDING + " expects a closure");
        }
        return new MacroValue(closure);
    }

    /** Opaque macro value, deliberately not an ordinary callable function. */
    static final class MacroValue implements TruffleObject {
        private final Closure closure;

        MacroValue(Closure closure) {
            this.closure = closure;
        }

        Closure closure() {
            return closure;
        }

        @Override
        public String toString() {
            return "#<macro>";
        }
    }
}
