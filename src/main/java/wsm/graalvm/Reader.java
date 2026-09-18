package wsm.graalvm;

import java.util.ArrayList;
import java.util.List;

/**
 * M0 reader: s-expressions, `'` sugar -> (QUOTE_HEAD form), dotted pairs,
 * exact integers, Cyrillic/Latin/Sanskrit symbols.
 *
 * Contract 4.0 (apostrophe) and 5.0 (decimal comma) get full treatment at
 * M1; the Canon contract itself uses only integers and internal apostrophe
 * tokens, which this reader already handles: internal apostrophes are part
 * of a symbol spelling because tokens end only at delimiters.
 */
public final class Reader {

    public static final Object QUOTE_HEAD = new Object() {
        @Override public String toString() { return "'"; }
    };

    public record Token(String spelling) {}

    private final String text;
    private int pos;

    public Reader(String text) { this.text = text; }

    public List<Object> readAll() {
        List<Object> forms = new ArrayList<>();
        while (true) {
            skipWs();
            if (pos >= text.length()) break;
            forms.add(readForm());
        }
        return forms;
    }

    private void skipWs() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ';') {
                while (pos < text.length() && text.charAt(pos) != '\n') pos++;
            } else if (Character.isWhitespace(c)) {
                pos++;
            } else {
                break;
            }
        }
    }

    private Object readForm() {
        skipWs();
        char c = text.charAt(pos);
        if (c == '\'' || c == '’') {
            pos++;
            return new Value.Pair(QUOTE_HEAD, readForm());
        }
        if (c == '(') { pos++; return readList(')'); }
        if (c == '[') { pos++; return readList(']'); }
        return readAtom();
    }

    private Object readList(char close) {
        List<Object> items = new ArrayList<>();
        Object tail = Value.NIL;
        while (true) {
            skipWs();
            if (pos >= text.length()) throw new WsmError(WsmError.Kind.PARSE, "unclosed list");
            char c = text.charAt(pos);
            if (c == close) { pos++; break; }
            if (c == '.' && isDelimiterChar(pos + 1)) {
                pos++;
                tail = readForm();
                skipWs();
                if (pos >= text.length() || text.charAt(pos) != close)
                    throw new WsmError(WsmError.Kind.PARSE, "dotted tail must end the list");
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
        return i >= text.length() || Character.isWhitespace(text.charAt(i))
                || text.charAt(i) == '(' || text.charAt(i) == ')' || text.charAt(i) == ';';
    }

    private Object readAtom() {
        int start = pos;
        while (pos < text.length() && text.charAt(pos) != '(' && text.charAt(pos) != ')'
                && text.charAt(pos) != ';' && text.charAt(pos) != '\'' && text.charAt(pos) != '’'
                && !Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        String token = text.substring(start, pos);
        if (token.isEmpty()) throw new WsmError(WsmError.Kind.PARSE, "empty token at " + start);
        // exact integer only when no leading zero ambiguity ("0001" is a registry id token)
        if (token.matches("[+-]?[1-9]\\d*|0")) {
            return Long.parseLong(token);
        }
        return new Token(token);
    }
}
