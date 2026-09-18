package wsm.graalvm;

public final class PairIdentityContract {
    public static void main(String[] args) {
        Object left = Value.pair(Value.symbol("a"), Value.NIL);
        Object right = Value.pair(Value.symbol("a"), Value.NIL);

        require(left != right, "separately allocated pairs must be distinct Java objects");
        require(!left.equals(right), "Value.Pair must not define Lisp structural equality through Java equals");
        require(WsmNode.Structural.equals(left, right),
                "explicit Structural.equals must own deep pair comparison");

        try {
            WsmNode.EqNode.eqRecord(left, right);
            throw new AssertionError("0003 eq must reject pair operands");
        } catch (WsmError error) {
            require(error.kind == WsmError.Kind.TYPE,
                    "0003 pair operands must fail with Type, got " + error.kind);
        }

        System.out.println("pair-identity-invariant runtime PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
