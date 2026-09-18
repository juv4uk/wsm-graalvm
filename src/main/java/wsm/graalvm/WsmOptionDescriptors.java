package wsm.graalvm;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionType;

/** Manual TruffleOptionDescriptors: exposes the numeric registry authority path. */
public final class WsmOptionDescriptors implements org.graalvm.options.OptionDescriptors {
    private final OptionDescriptors delegate = OptionDescriptors.create(java.util.List.of(
            OptionDescriptor.newBuilder(WsmOptionsKey.REGISTRY_PATH, "wsm.registryPath")
                    .help("path to numeric semantic surface authority (semantic-registry.lisp)")
                    .stability(org.graalvm.options.OptionStability.EXPERIMENTAL)
                    .build()));

    WsmOptionDescriptors() {}

    @Override
    public OptionDescriptor get(String name) { return delegate.get(name); }

    @Override
    public java.util.Iterator<OptionDescriptor> iterator() { return delegate.iterator(); }
}
