package wsm.graalvm;

import java.util.Arrays;
import java.util.List;

/**
 * Observable error vocabulary owned by my-lisp Contract 3.0.
 *
 * Internal Java control flow may vary, but every surfaced failure must map to
 * exactly one of these eight contract names. Adding/removing a kind is a
 * semantic change, not a local refactor.
 */
public final class WsmError extends RuntimeException {

    public enum Kind {
        PARSE("Parse"),
        UNKNOWN_SYMBOL("UnknownSymbol"),
        ARITY("Arity"),
        TYPE("Type"),
        INVALID_FORM("InvalidForm"),
        OUT_OF_MEMORY("OutOfMemory"),
        NUMERIC_OVERFLOW("NumericOverflow"),
        DIVISION_BY_ZERO("DivisionByZero");

        private final String contractName;

        Kind(String contractName) {
            this.contractName = contractName;
        }

        public String contractName() {
            return contractName;
        }
    }

    private static final List<String> CLOSED_VOCABULARY = List.of(
            "Parse",
            "UnknownSymbol",
            "Arity",
            "Type",
            "InvalidForm",
            "OutOfMemory",
            "NumericOverflow",
            "DivisionByZero"
    );

    public final Kind kind;
    public final String detail;

    public WsmError(Kind kind, String detail) {
        super(kind.contractName() + ": " + detail);
        this.kind = kind;
        this.detail = detail;
    }

    public String contractKind() {
        return kind.contractName();
    }

    public static List<String> closedVocabulary() {
        List<String> actual = Arrays.stream(Kind.values())
                .map(Kind::contractName)
                .toList();
        if (!actual.equals(CLOSED_VOCABULARY)) {
            throw new IllegalStateException(
                    "ErrorKind vocabulary drift: expected "
                            + CLOSED_VOCABULARY + ", got " + actual);
        }
        return CLOSED_VOCABULARY;
    }

    public static void arity(Object[] args, int expected, String id) {
        if (args.length != expected) {
            throw new WsmError(
                    Kind.ARITY,
                    id + ": expected " + expected
                            + " argument(s), received " + args.length);
        }
    }

    static void arityCheck(String text) {
        if (text.length() > 4_000_000) {
            throw new WsmError(
                    Kind.NUMERIC_OVERFLOW,
                    "registry/text exceeds bounded parse size");
        }
    }
}
