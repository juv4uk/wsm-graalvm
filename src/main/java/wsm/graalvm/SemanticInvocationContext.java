package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;

/**
 * Runtime context passed only to mechanisms that need the current execution
 * environment. Semantic identity remains the numeric ID; this object carries
 * execution state, not language meaning.
 */
record SemanticInvocationContext(
        WsmLanguage language,
        CanonRegistry registry,
        GlobalBindings globals,
        LexicalScope scope,
        VirtualFrame frame) {}
