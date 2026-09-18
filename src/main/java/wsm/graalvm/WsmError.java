package wsm.graalvm;

/** ErrorKind is observable semantics (Contract 3.0). */
public final class WsmError extends RuntimeException {
    public enum Kind { PARSE, ARITY, INVALID_FORM, TYPE, DIVISION_BY_ZERO, NUMERIC_OVERFLOW }
    public final Kind kind;
    public final String detail;

    public WsmError(Kind kind, String detail) {
        super(kind + ": " + detail);
        this.kind = kind;
        this.detail = detail;
    }

    public static void arity(Object[] args, int expected, String id) {
        if (args.length != expected)
            throw new WsmError(Kind.ARITY, id + ": expected " + expected
                    + " argument(s), received " + args.length);
    }

    static void arityCheck(String text) {
        if (text.length() > 4_000_000)
            throw new WsmError(Kind.NUMERIC_OVERFLOW, "registry/text exceeds bounded parse size");
    }
}
