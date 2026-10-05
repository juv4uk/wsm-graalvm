package wsm.graalvm;

/** Contract witness for Lisp-owned exact-domain human surface projection. */
public final class DomainSurfaceRegistryContract {
    private DomainSurfaceRegistryContract() {}

    public static void main(String[] args) {
        String d14 = """
                (domain-surfaces-d1-d4
                  (schema domain-surfaces-d1-d4/1)
                  (status projection-only)
                  (identity-law "surface -> exact (domain,bits) -> domain law")
                  (languages en uk sa)
                  (row D3 "100" function "car" "перше" "ādi" selected stable-donor)
                  (row D4 "1111" function "append" "приєднати" "saṅkalana" selected stable-donor))
                """;

        String d5 = """
                (domain-surfaces-d5
                  (schema domain-surfaces-d5/1)
                  (status projection-only)
                  (identity-law "surface -> exact (D5,bits) -> D5 law")
                  (languages en uk sa)
                  (row D5 "10100" function "reverse" "зворот" "viloma" stable-donor stable-donor))
                """;

        DomainSurfaceRegistry registry = DomainSurfaceRegistry.load(d14, d5);

        requireIdentity(registry, "en", "car", "D3:100");
        requireIdentity(registry, "ук", "перше", "D3:100");
        requireIdentity(registry, "sa", "ādi", "D3:100");

        requireIdentity(registry, "en", "append", "D4:1111");
        requireIdentity(registry, "ук", "приєднати", "D4:1111");

        DomainSurfaceRegistry.Surface reverse = registry.resolve("en", "reverse");
        require(reverse != null, "D5 surface must resolve");
        require("D5:10100".equals(reverse.identity().toString()), "D5 width must be preserved");
        require("function".equals(reverse.role()), "surface role must be preserved");

        DomainSurfaceRegistry.Surface display = DomainSurfaceRegistry.load("""
                (domain-surfaces-d1-d4
                  (schema domain-surfaces-d1-d4/1)
                  (row D3 "000" display "empty" "порожнє" "śūnya" selected selected))
                """).resolve("en", "empty");
        require(display != null, "display surface must still be loadable");
        require("display".equals(display.role()), "display must remain non-callable metadata");

        System.out.println("DOMAIN-SURFACE-REGISTRY-CONTRACT-GREEN");
    }

    private static void requireIdentity(
            DomainSurfaceRegistry registry,
            String namespace,
            String spelling,
            String expected) {
        DomainSurfaceRegistry.Surface surface = registry.resolve(namespace, spelling);
        require(surface != null, namespace + ":" + spelling + " must resolve");
        require(expected.equals(surface.identity().toString()),
                namespace + ":" + spelling + " -> expected " + expected
                        + ", got " + surface.identity());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
