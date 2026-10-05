package wsm.graalvm;

/**
 * Current-domain production-boundary witness for the first structural D3
 * mechanisms. Predicate/control D3 identities remain blocked until their
 * exact D1/current laws are implemented rather than reusing old records.
 */
public final class DomainIdentityMechanismContract {
    private DomainIdentityMechanismContract() {}

    public static void main(String[] args) {
        DomainIdentity cons = DomainIdentity.exact(3, "111");
        DomainIdentity car = DomainIdentity.exact(3, "100");
        DomainIdentity cdr = DomainIdentity.exact(3, "011");
        DomainIdentity atom = DomainIdentity.exact(3, "010");
        DomainIdentity eq = DomainIdentity.exact(3, "101");
        DomainIdentity cond = DomainIdentity.exact(3, "110");

        require(SemanticMechanismTable.supports(cons), "D3:111 CONS must be admitted");
        require(SemanticMechanismTable.supports(car), "D3:100 CAR must be admitted");
        require(SemanticMechanismTable.supports(cdr), "D3:011 CDR must be admitted");

        require(!SemanticMechanismTable.supports(atom),
                "D3:010 ATOM must remain blocked until exact D1 PredicateBit semantics are routed");
        require(!SemanticMechanismTable.supports(eq),
                "D3:101 EQ must remain blocked until exact D1 PredicateBit semantics are routed");
        require(!SemanticMechanismTable.supports(cond),
                "D3:110 COND must remain blocked until current clause semantics are routed");

        Value.Pair pair = new Value.Pair(Value.NIL, Value.NIL);
        require(SemanticMechanismTable.invoke(car, new Object[] {pair}) == Value.NIL,
                "D3:100 CAR must preserve the existing pair projection");
        require(SemanticMechanismTable.invoke(cdr, new Object[] {pair}) == Value.NIL,
                "D3:011 CDR must preserve the existing pair projection");

        Object constructed = SemanticMechanismTable.invoke(
                cons, new Object[] {Value.NIL, Value.NIL});
        require(constructed instanceof Value.Pair,
                "D3:111 CONS must return a pair");

        Value.SemanticRef ref = new Value.SemanticRef(car);
        require(ref.id().equals(car), "SemanticRef must carry exact DomainIdentity");
        require("D3:100".equals(Printer.print(ref)),
                "current semantic references must print exact domain identity");

        Value.LegacySemanticRef legacy = new Value.LegacySemanticRef(
                Sid8.parseBareToken("00000101"));
        require("00000101".equals(Printer.print(legacy)),
                "legacy reference must remain visibly separate from current identity");

        System.out.println("DOMAIN-IDENTITY-MECHANISM-CONTRACT-GREEN");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
