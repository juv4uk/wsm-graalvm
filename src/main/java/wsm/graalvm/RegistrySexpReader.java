package wsm.graalvm;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal S-expression reader for semantic-registry.lisp as DATA.
 *
 * Deliberately not the language Reader: registry surface spellings such as
 * apostrophe (') are atoms in this schema, not quote syntax.
 */
final class RegistrySexpReader {
    private final String text;
    private int pos;

    RegistrySexpReader(String text) {
        this.text = text;
    }

    List<Object> readAll() {
        List<Object> forms = new ArrayList<>();
        while (true) {
            skipSpaceAndComments();
            if (pos >= text.length()) return forms;
            forms.add(readForm());
        }
    }

    private Object readForm() {
        skipSpaceAndComments();
        if (pos >= text.length()) {
            throw new WsmError(WsmError.Kind.PARSE, "unexpected end of registry");
        }
        if (text.charAt(pos) == '(') {
            pos++;
            return readList();
        }
        if (text.charAt(pos) == ')') {
            throw new WsmError(WsmError.Kind.PARSE, "unexpected ) in registry");
        }
        if (text.charAt(pos) == '"') {
            return readString();
        }
        return readAtom();
    }

    private List<Object> readList() {
        List<Object> items = new ArrayList<>();
        while (true) {
            skipSpaceAndComments();
            if (pos >= text.length()) {
                throw new WsmError(WsmError.Kind.PARSE, "unclosed registry list");
            }
            if (text.charAt(pos) == ')') {
                pos++;
                return items;
            }
            items.add(readForm());
        }
    }

    private String readString() {
        pos++; // opening quote
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw new WsmError(WsmError.Kind.PARSE, "unterminated registry string escape");
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
                        "unsupported registry string escape: \\" + escaped);
            }
        }
        throw new WsmError(WsmError.Kind.PARSE, "unterminated registry string");
    }

    private String readAtom() {
        int start = pos;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c) || c == '(' || c == ')' || c == ';') {
                break;
            }
            pos++;
        }
        if (start == pos) {
            throw new WsmError(WsmError.Kind.PARSE, "empty registry atom");
        }
        return text.substring(start, pos);
    }

    private void skipSpaceAndComments() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
            } else if (c == ';') {
                while (pos < text.length() && text.charAt(pos) != '\n') pos++;
            } else {
                return;
            }
        }
    }
}
