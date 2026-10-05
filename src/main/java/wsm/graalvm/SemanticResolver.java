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
        String spelling = token.spelling();

        // Canonical current D3 source carries its own exact three-bit identity.
        // The domain is established by the source word width at the frontend
        // boundary; downstream execution receives the explicit pair.
        if (spelling.matches("[01]{3}")) {
            return new Semantic(DomainIdentity.exact(3, spelling));
        }

        Sid8 id = registry.semanticIdForToken(spelling);
        if (id != null) return new LegacySemantic(id);
        return new Lexical(spelling);
    }
}
