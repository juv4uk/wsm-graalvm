package wsm.graalvm;

import wsm.graalvm.Reader.Token;

/**
 * One-way frontend boundary: surface token -> current exact-domain identity,
 * explicit historical compatibility identity, or ordinary lexical symbol.
 *
 * Current execution uses DomainIdentity. Sid8 survives only as an explicitly
 * typed legacy compatibility result.
 */
public final class SemanticResolver {

    public sealed interface Resolution permits Semantic, LegacySemantic, Lexical {}

    public record Semantic(DomainIdentity id) implements Resolution {}

    public record LegacySemantic(Sid8 id) implements Resolution {}

    public record Lexical(String spelling) implements Resolution {}

    private final CanonRegistry registry;

    public SemanticResolver(CanonRegistry registry) {
        this.registry = registry;
    }

    public Resolution resolve(Token token) {
        Sid8 id = registry.semanticIdForToken(token.spelling());
        if (id == null) return new Lexical(token.spelling());

        DomainIdentity current = LegacySid8Projection.toCurrentDomainIdentity(id);
        if (current != null) return new Semantic(current);

        return new LegacySemantic(id);
    }
}
