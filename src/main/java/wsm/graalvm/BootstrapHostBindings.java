package wsm.graalvm;

/**
 * Narrow host mechanism required by the upstream macro derivation.
 *
 * make-macro is intentionally not a semantic registry identity and is not
 * compiler head-dispatch. It is an ordinary first-class binding that converts
 * an evaluated Closure into a MacroValue, matching my-lisp's macro substrate.
 */
final class BootstrapHostBindings {
    private static final String MAKE_MACRO = "make-macro";

    private BootstrapHostBindings() {}

    static void install(GlobalBindings globals) {
        globals.define(MAKE_MACRO, new WsmFunc() {
            @Override
            public Object call(Object[] args) {
                WsmError.arity(args, 1, MAKE_MACRO);
                if (!(args[0] instanceof Closure closure)) {
                    throw new WsmError(
                            WsmError.Kind.TYPE,
                            MAKE_MACRO + " expects a closure");
                }
                return new MacroValue(closure);
            }
        });
    }
}
