package wsm.graalvm;

import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

/**
 * Options of the substrate. The registryPath points to the numeric
 * surface authority (semantic-registry.lisp) so that the evaluator never
 * includes a hardcoded Lisp surface spelling.
 */
final class WsmOptionsKey {
    static final OptionKey<String> REGISTRY_PATH =
            new OptionKey<>("", new OptionType<>("wsmPath", s -> s));

    private WsmOptionsKey() {}
}
