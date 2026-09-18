package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Runtime context passed only to mechanisms that need current execution state.
 * Semantic identity remains the numeric ID; this object carries no language meaning.
 */
record SemanticInvocationContext(
        WsmLanguage language,
        CanonRegistry registry,
        GlobalBindings globals,
        LexicalScope scope,
        VirtualFrame frame) {
    SemanticInvocationContext withFrame(VirtualFrame currentFrame) {
        return new SemanticInvocationContext(
                language, registry, globals, scope, currentFrame);
    }
}
