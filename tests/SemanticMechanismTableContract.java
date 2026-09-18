package wsm.graalvm;

/**
 * Contract for #3: mechanism dispatch begins only after semantic resolution.
 */
public final class SemanticMechanismTableContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expectType(String id) {
        try {
            SemanticMechanismTable.invoke(id, new Object[0]);
            throw new AssertionError("expected TYPE for non-callable/unresolved id: " + id);
        } catch (WsmError error) {
            require(error.kind == WsmError.Kind.TYPE,
                    "expected TYPE for " + id + ", got " + error.kind);
        }
    }

    public static void main(String[] args) {
        require(SemanticMechanismTable.supports("0002"), "0002 mechanism missing");
        require(SemanticMechanismTable.supports("0003"), "0003 mechanism missing");
        require(SemanticMechanismTable.supports("0004"), "0004 mechanism missing");
        require(SemanticMechanismTable.supports("0005"), "0005 mechanism missing");
        require(SemanticMechanismTable.supports("0006"), "0006 mechanism missing");

        require(!SemanticMechanismTable.supports("car"),
                "mechanism table must not accept source spelling");
        require(!SemanticMechanismTable.supports("перше"),
                "mechanism table must not accept Ukrainian source spelling");
        require(!SemanticMechanismTable.supports("ādi"),
                "mechanism table must not accept Sanskrit source spelling");

        Object pair = SemanticMechanismTable.invoke(
                "0004",
                new Object[]{Value.symbol("left"), Value.symbol("right")});

        Object first = SemanticMechanismTable.invoke("0005", new Object[]{pair});
        Object rest = SemanticMechanismTable.invoke("0006", new Object[]{pair});

        require(first.equals(Value.symbol("left")), "0005 projection mismatch");
        require(rest.equals(Value.symbol("right")), "0006 projection mismatch");

        require(Printer.print(SemanticMechanismTable.invoke(
                "0002", new Object[]{pair})).equals("(structural-kind pair)"),
                "0002 must keep current structural-kind semantics");

        require(Printer.print(SemanticMechanismTable.invoke(
                "0003", new Object[]{Value.symbol("x"), Value.symbol("x")}))
                .equals("(identity-relation same)"),
                "0003 must keep current identity-relation semantics");

        expectType("0001"); // quote is syntax-only
        expectType("0007"); // cond is syntax-only
        expectType("car");  // spelling must have been resolved before this boundary

        System.out.println("SEMANTIC-MECHANISM-TABLE-CONTRACT-OK");
    }
}
