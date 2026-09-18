package wsm.graalvm;

/**
 * Contract for #4: registry resolution is the last place surface spelling
 * participates in language-owned dispatch.
 */
public final class SemanticResolverContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static CanonRegistry registry(String stableName) {
        String source =
                "(sr/1 " +
                "  (0005 (en " + stableName + " stable) " +
                "        (uk старе compatibility-only) " +
                "        (sa кандидат candidate))" +
                ")";
        return new CanonRegistry().load(source);
    }

    public static void main(String[] args) {
        CanonRegistry first = registry("комета");
        SemanticResolver resolver = new SemanticResolver(first);

        require(first.semanticIdForToken("0005").equals("0005"),
                "machine ID must resolve to itself");
        require(first.semanticIdForToken("комета").equals("0005"),
                "stable surface must resolve to numeric ID");
        require(first.semanticIdForToken("старе").equals("0005"),
                "compatibility-only surface remains admitted");
        require(first.semanticIdForToken("кандидат") == null,
                "candidate surface must not become runtime identity");

        SemanticResolver.Resolution semantic = resolver.resolve(new Reader.Token("комета"));
        require(semantic instanceof SemanticResolver.Semantic s && s.id().equals("0005"),
                "resolved language surface must lose spelling and keep only ID");

        SemanticResolver.Resolution machine = resolver.resolve(new Reader.Token("0005"));
        require(machine instanceof SemanticResolver.Semantic s && s.id().equals("0005"),
                "numeric machine ID must take the same semantic route");

        SemanticResolver.Resolution user = resolver.resolve(new Reader.Token("Комета"));
        require(user instanceof SemanticResolver.Lexical l && l.spelling().equals("Комета"),
                "ordinary user symbol must preserve exact spelling");

        // Prove routing is registry-driven, not hardcoded in Java.
        CanonRegistry changed = registry("астероїд");
        require(changed.semanticIdForToken("комета") == null,
                "old surface must disappear when registry changes");
        require(changed.semanticIdForToken("астероїд").equals("0005"),
                "new registry surface must route without Java source changes");

        System.out.println("SEMANTIC-RESOLVER-CONTRACT-OK");
    }
}
