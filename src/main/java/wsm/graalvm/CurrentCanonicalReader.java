package wsm.graalvm;

import java.util.ArrayList;
import java.util.List;

/**
 * Narrow frontend for the current Contract 11.6 D2/D3 canonical-source slice.
 *
 * D2 owns syntax structure: 10=open, 00=separator, 01=close.
 * D3 owns the admitted foundation words. D3:000 materializes structural empty;
 * every other D3 word is carried as an exact DomainIdentity.
 *
 * This parser intentionally does not consult human surfaces, CanonRegistry,
 * Sid8, decimal numbers, or wider domains.
 */
public final class CurrentCanonicalReader {
    private static final DomainIdentity D3_EMPTY = DomainIdentity.exact(3, "000");

    public record ParseResult(Object form, List<DomainIdentity> identityTrace) {
        public ParseResult {
            identityTrace = List.copyOf(identityTrace);
        }
    }

    private final List<String> tokens;
    private final List<DomainIdentity> trace = new ArrayList<>();
    private int pos;

    private CurrentCanonicalReader(String source) {
        if (source == null) {
            throw new WsmError(WsmError.Kind.PARSE, "canonical source must be non-null");
        }
        String trimmed = source.trim();
        this.tokens = trimmed.isEmpty()
                ? List.of()
                : List.of(trimmed.split("\\s+"));
    }

    public static ParseResult parseOne(String source) {
        CurrentCanonicalReader reader = new CurrentCanonicalReader(source);
        if (reader.tokens.isEmpty()) {
            throw new WsmError(WsmError.Kind.PARSE, "canonical source is empty");
        }
        Object form = reader.readForm();
        if (reader.pos != reader.tokens.size()) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "trailing canonical token: " + reader.tokens.get(reader.pos));
        }
        return new ParseResult(form, reader.trace);
    }

    private Object readForm() {
        if (pos >= tokens.size()) {
            throw new WsmError(WsmError.Kind.PARSE, "expected canonical form");
        }

        String token = tokens.get(pos++);
        if ("10".equals(token)) {
            return readList();
        }
        if ("00".equals(token) || "01".equals(token) || "11".equals(token)) {
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "D2 structure token outside list context: " + token);
        }
        if (token.matches("[01]{3}")) {
            DomainIdentity identity = DomainIdentity.exact(3, token);
            if (identity.equals(D3_EMPTY)) {
                return Value.NIL;
            }
            trace.add(identity);
            return new Value.SemanticRef(identity);
        }

        throw new WsmError(
                WsmError.Kind.PARSE,
                "unsupported exact-width token in D2/D3 profile: " + token);
    }

    private Object readList() {
        List<Object> items = new ArrayList<>();

        if (peek("01")) {
            pos++;
            return Value.NIL;
        }

        while (true) {
            items.add(readForm());
            if (pos >= tokens.size()) {
                throw new WsmError(WsmError.Kind.PARSE, "unclosed D2 list");
            }

            String delimiter = tokens.get(pos++);
            if ("01".equals(delimiter)) {
                return Value.list(items);
            }
            if ("00".equals(delimiter)) {
                if (pos >= tokens.size() || "01".equals(tokens.get(pos))) {
                    throw new WsmError(
                            WsmError.Kind.PARSE,
                            "D2 separator must be followed by a form");
                }
                continue;
            }
            if ("11".equals(delimiter)) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "D2 dotted structure is outside the current bounded profile");
            }
            throw new WsmError(
                    WsmError.Kind.PARSE,
                    "expected D2 separator/close, got: " + delimiter);
        }
    }

    private boolean peek(String token) {
        return pos < tokens.size() && token.equals(tokens.get(pos));
    }
}
