package wsm.graalvm;

import wsm.graalvm.Reader.Token;

/**
 * Converts reader syntax objects into ordinary Lisp data values.
 *
 * This is mechanism only: it preserves the exact datum shape and never
 * performs evaluation or semantic-registry resolution.
 */
public final class ReaderDatum {
    private ReaderDatum() {}

    public static Object toValue(Object form) {
        if (form == Value.NIL) return Value.NIL;
        if (form instanceof Long) return form;
        if (form instanceof Token token) return Value.symbol(token.spelling());
        if (form instanceof Value.Pair pair) {
            return new Value.Pair(toValue(pair.car), toValue(pair.cdr));
        }
        throw new WsmError(
                WsmError.Kind.INVALID_FORM,
                "reader form cannot become Lisp datum: " + form);
    }
}
