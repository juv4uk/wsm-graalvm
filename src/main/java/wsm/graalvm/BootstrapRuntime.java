package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Executes source inside one WSM runtime context and returns the raw guest
 * value before Polyglot wrapping.
 *
 * This is bootstrap plumbing only: source parsing, Compiler semantics, and
 * runtime behavior remain the normal WSM path.
 */
final class BootstrapRuntime {
    private BootstrapRuntime() {}

    static Object execute(WsmContext context, String source) {
        List<Object> forms = new Reader(source).readAll();
        return new BootstrapRootNode(context, forms).getCallTarget().call();
    }

    /**
     * Create one shared runtime context and load the manifest-selected pinned
     * Lisp bootstrap closure into it.
     *
     * Source meaning remains in external/my-lisp. This method only performs
     * transport/execution plus the narrow semantic-0012 MacroValue projection.
     */
    static WsmContext bootstrapPinned(Path repositoryRoot) throws IOException {
        BootstrapClosureLoader.Closure closure =
                BootstrapClosureLoader.load(repositoryRoot);
        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        for (BootstrapClosureLoader.Source source : closure.executableSources()) {
            Object value = execute(context, source.text());
            if (source.path().endsWith("lib/macro.lisp")) {
                if (!(value instanceof GlobalBindings.MacroValue macro)) {
                    throw new WsmError(
                            WsmError.Kind.INVALID_FORM,
                            "pinned lib/macro.lisp did not return MacroValue");
                }
                MacroPeerInstaller.install(context.registry(), context.globals(), macro);
            }
        }
        return context;
    }

    private static final class BootstrapRootNode extends RootNode {
        @Child private SequentialProgramBodyNode body;

        BootstrapRootNode(WsmContext context, List<Object> forms) {
            super(null);
            this.body = new SequentialProgramBodyNode(null, context, forms);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return body.executeGeneric(frame);
        }

        @Override
        public String getName() {
            return "wsm-bootstrap";
        }
    }
}
