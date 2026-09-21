package wsm.graalvm;

import java.util.ArrayList;
import java.util.List;

/**
 * Reader for the current my-lisp contract.
 *
 * Contract 4.0:
 * - apostrophe at expression start is quote syntax;
 * - apostrophe inside an identifier is an ordinary identifier character.
 *
 * The reader uses an internal QUOTE_HEAD marker, never a hardcoded human
 * surface spelling. Compiler lowering maps that marker to Canon identity 0001.
 */
public final class Reader {

    public static final Object QUOTE_HEAD = new Object() {
        @Override public String toString() { return "#<quote-syntax:0001>"; }
    };

    public record Token(String spelling) {}

    private final String text;
    private int pos;

    public Reader(String text) {
        this.text = text;
    }

    public List<Object> readAll() {
        List<Object> forms = new ArrayList<>();
        while (true) {
            skipWs();
            if (pos >= text.length()) break;
            Object form = readForm();
            if (isBinaryFormatDeclaration(form)) {
                // A leading (binary WIDTH) is source-format metadata that
                // activates fixed-width binary token syntax; it is not a
                // form to evaluate. Upstream contract: the declaration is
                // consumed by the reader, never executed as a call to SID
                // 10101001 (binary).
                continue;
            }
            forms.add(form);
        }
        return forms;
    }

    private static boolean isBinaryFormatDeclaration(Object form) {
        if (!(form instanceof Value.Pair pair)) return false;
        Object head = pair.car;
        if (head instanceof Token token && token.spelling().equals("binary")) {
            Object width = pair.cdr instanceof Value.Pair widthCell
                    ? widthCell.car
                    : null;
            if (width instanceof Token widthToken) {
                return widthToken.spelling().matches("[1-9]\\d*");
            }
            if (width instanceof Value.NumberValue number) {
                return number.denominator().equals(java.math.BigInteger.ONE)
                        && number.numerator().compareTo(java.math.BigInteger.ONE) >= 0;
            }
        }
        return false;
    }

    private void skipWs() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ';') {
                while (pos < text.length() && text.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (Character.isWhitespace(c)) {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object readForm() {
        skipWs();
        if (pos >= text.length()) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "expected expression after reader syntax");
        }

        char c = text.charAt(pos);
        if (c == '\'' || c == '’') {
            pos++;
            skipWs();
            if (pos >= text.length()) {
                throw new WsmError(
                        WsmError.Kind.PARSE,
                        "apostrophe must be followed by an expression");
            }

            // Proper internal form:
            //   'x  ->  (QUOTE_HEAD x)
            // QUOTE_HEAD is an internal syntax identity, not a surface word.
            Object datum = readForm();
            return new Value.Pair(
                    QUOTE_HEAD,
                    new Value.Pair(datum, Value.NIL));
        }

        if (c == '"') {
            return readString();
        }
        if (c == '(') {
            pos++;
            return readList(')');
        }
        if (c == '[') {
            pos++;
            return readList(']');
        }
        return readAtom();
    }

    private Object readList(char close) {
        List<Object> items = new ArrayList<>();
        Object tail = Value.NIL;

        while (true) {
            skipWs();
            if (pos >= text.length()) {
                throw new WsmError(
                        WsmError.Kind.PARSE,
                        "unclosed list");
            }

            char c = text.charAt(pos);
            if (c == close) {
                pos++;
                break;
            }

            if (c == '.' && isDelimiterChar(pos + 1)) {
                pos++;
                tail = readForm();
                skipWs();
                if (pos >= text.length() || text.charAt(pos) != close) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "dotted tail must end the list");
                }
                pos++;
                break;
            }

            items.add(readForm());
        }

        Object result = tail;
        for (int i = items.size() - 1; i >= 0; i--) {
            result = new Value.Pair(items.get(i), result);
        }
        return result;
    }

    private boolean isDelimiterChar(int i) {
        if (i >= text.length()) return true;
        char c = text.charAt(i);
        return Character.isWhitespace(c)
                || c == '(' || c == ')'
                || c == '[' || c == ']'
                || c == ';';
    }

    private Object readString() {
        pos++; // opening quote
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return new Value.StringValue(out.toString());
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw new WsmError(WsmError.Kind.PARSE, "unterminated string escape");
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                default -> throw new WsmError(
                        WsmError.Kind.PARSE,
                        "unsupported string escape: \\" + escaped);          }
        }
        throw new WsmError(WsmError.Kind.PARSE, "unterminated string literal");
    }

    private Object readAtom() {
        int start = pos;

        // Apostrophes are intentionally NOT delimiters here. If the atom has
        // already started, Contract 4.0 says they are identifier characters.
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c)
                    || c == '(' || c == ')'
                    || c == '[' || c == ']'
                    || c == ';') {
                break;
            }
            pos++;
        }

        String token = text.substring(start, pos);
        if (token.isEmpty()) {
            throw new WsmError(WsmError.Kind.PARSE, "empty token at " + start);
        }

        // Exact bare 8-bit spellings are semantic identities, never decimals.
        // A token such as 10101001 must stay a Token so the Compiler can
        // resolve it through CanonRegistry, not be reconstructed as a decimal
        // NumberValue. This mirrors the upstream fixed-width binary reader.
        if (token.matches("[01]{8}")) {
            return new Token(token);
        }
        // Numeric machine IDs such as 0001 remain symbols, not numbers.
        if (token.matches("[+-]?[1-9]\\d*|0")) {
            return Value.NumberValue.integer(new java.math.BigInteger(token));
        }
        if (token.matches("[+-]?(?:\\d+[.,]\\d*|[.,]\\d+)(?:[eE][+-]?\\d+)?")
                || token.matches("[+-]?[1-9]\\d*(?:[eE][+-]?\\d+)")) {
            return Value.NumberValue.decimal(token.replace(',', '.'));
        }
        if (token.matches("[+-]?\\d+/\\d+")) {
            String[] parts = token.split("/", -1);
            return new Value.NumberValue(
                    new java.math.BigInteger(parts[0]),
                    new java.math.BigInteger(parts[1]));
        }
        return new Token(token);
    }
}