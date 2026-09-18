package wsm.graalvm;

/** RED/green contract: conformance fixture data requires real string literals. */
public final class ReaderStringContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Object form = new Reader("((expr . \"(quote radio)\") (tier . 1))").readAll().get(0);
        require(form instanceof Value.Pair, "fixture form must be a list");

        Value.Pair outer = (Value.Pair) form;
        Value.Pair exprEntry = (Value.Pair) outer.car;
        require(exprEntry.cdr instanceof Value.StringValue,
                "expr value must be a language StringValue, got "
                        + exprEntry.cdr.getClass().getName());
        require(((Value.StringValue) exprEntry.cdr).value.equals("(quote radio)"),
                "string literal contents must be preserved exactly");

        Object escaped = new Reader("\"line\\n\\t\\\"\\\\\"").readAll().get(0);
        require(escaped instanceof Value.StringValue,
                "escaped literal must be a language StringValue");
        require(((Value.StringValue) escaped).value.equals("line\n\t\"\\"),
                "standard escapes must decode deterministically");

        Object commaDecimal = new Reader("12,455").readAll().get(0);
        Object dotDecimal = new Reader("12.455").readAll().get(0);
        require(commaDecimal instanceof Value.NumberValue,
                "decimal comma must read as exact NumberValue");
        require(commaDecimal.equals(dotDecimal),
                "12,455 must denote the same exact rational as 12.455");

        Object numericId = new Reader("0001").readAll().get(0);
        require(numericId instanceof Reader.Token,
                "numeric semantic IDs with leading zero must remain symbols");

        Object commaSymbol = new Reader("alpha,beta").readAll().get(0);
        require(commaSymbol instanceof Reader.Token,
                "nonnumeric comma token must remain a symbol");

        System.out.println("READER-STRING-CONTRACT-OK");
    }
}
