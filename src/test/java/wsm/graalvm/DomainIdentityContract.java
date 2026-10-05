package wsm.graalvm;

/** RED/green contract witnesses for exact SENS domain identity transport. */
public final class DomainIdentityContract {
    private DomainIdentityContract() {}

    public static void main(String[] args) {
        DomainIdentity d3 = new DomainIdentity(3, "000");
        DomainIdentity d3Same = DomainIdentity.exact(3, "000");
        DomainIdentity d4 = new DomainIdentity(4, "0000");

        require(d3.equals(d3Same), "equal domain+bits must be equal");
        require(!d3.equals(d4), "equal-looking payloads in different domains must not alias");
        require("D3:000".equals(d3.toString()), "identity rendering must preserve domain and bits");

        expectFailure(() -> new DomainIdentity(3, "00"), "width mismatch must fail closed");
        expectFailure(() -> new DomainIdentity(3, "00x"), "non-binary payload must fail closed");
        expectFailure(() -> new DomainIdentity(0, ""), "non-positive domain must fail closed");

        System.out.println("DOMAIN-IDENTITY-CONTRACT-GREEN");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
