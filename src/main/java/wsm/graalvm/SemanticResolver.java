package wsm.graalvm;

import wsm.graalvm.Reader.Token;

/**
 * One-way frontend boundary: source token -> semantic ID or ordinary symbol.
 *
 * Once a token resolves to Semantic, downstream execution must use only the
 * exact Sid8. Ordinary user-level symbols preserve their exact spelling.
 */
public final class SemanticResolver {

    public sealed interface Resolution permits Semantic, Lexical {}

    public record Semantic(Sid8 id) implements Resolution {}

    public record Lexical(String spelling) implements Resolution {}

    private final CanonRegistry registry;

    public SemanticResolver(CanonRegistry registry) {
        this.registry = registry;
    }

    public Resolution resolve(Token token) {
        Sid8 id = registry.semanticIdForToken(token.spelling());
        if (id != null) return new Semantic(id);
        return new Lexical(token.spelling());
    }
}
