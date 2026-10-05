package wsm.graalvm;

import java.util.List;

/** Focused witness for the current exact D2/D3 canonical-source frontend. */
public final class CurrentCanonicalSourceContract {
    private CurrentCanonicalSourceContract() {}

    public static void main(String[] args) {
        DomainIdentity quote = DomainIdentity.exact(3, "001");

        CurrentCanonicalReader.ParseResult parsed =
                CurrentCanonicalReader.parseOne("10 001 00 000 01");

        require(parsed.identityTrace().equals(List.of(quote)),
                "QUOTE(empty) trace must contain exactly D3:001");
        require(parsed.form() instanceof Value.Pair,
                "canonical open/close must materialize list structure");

        Value.Pair call = (Value.Pair) parsed.form();
        require(call.car instanceof Value.SemanticRef ref && ref.id().equals(quote),
                "head must be exact D3:001 SemanticRef");
        require(call.cdr instanceof Value.Pair args
                        && args.car == Value.NIL
                        && args.cdr == Value.NIL,
                "D3:000 argument must materialize structural empty, not SemanticRef");

        Compiler compiler = new Compiler(new CanonRegistry());
        WsmNode node = compiler.compile(parsed.form(), compiler.root());
        Object value = node.executeGeneric(null);
        require(value == Value.NIL,
                "current D3 QUOTE(empty) must execute to structural empty");

        CurrentCanonicalReader.ParseResult empty =
                CurrentCanonicalReader.parseOne("000");
        require(empty.form() == Value.NIL,
                "bare D3:000 must be structural empty");
        require(empty.identityTrace().isEmpty(),
                "structural empty must not be reported as a callable identity");

        expectFailure("10 001 000 01", WsmError.Kind.PARSE);
        expectFailure("10 001 00 01", WsmError.Kind.PARSE);
        expectFailure("11", WsmError.Kind.PARSE);
        expectFailure("00000001", WsmError.Kind.PARSE);
        expectFailure("10 001 11 000 01", WsmError.Kind.INVALID_FORM);

        System.out.println("CURRENT-CANONICAL-SOURCE-CONTRACT-GREEN");
    }

    private static void expectFailure(String source, WsmError.Kind kind) {
        try {
            CurrentCanonicalReader.parseOne(source);
        } catch (WsmError error) {
            require(error.kind == kind,
                    "expected " + kind + " for " + source + ", got " + error.contractKind());
            return;
        }
        throw new AssertionError("expected fail-closed parse for " + source);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
