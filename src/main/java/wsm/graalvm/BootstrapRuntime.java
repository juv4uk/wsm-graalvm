package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Child;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.List;

/**
 * Executes source inside one WSM runtime context and returns the raw guest
 * value before Polyglot wrapping.
 *
 * This is bootstrap plumbing only: source parsing, Compiler semantics, and
 * runtime behavior remain exactly the normal WSM path.
 */
final class BootstrapRuntime {

    private BootstrapRuntime() {}

    static Object execute(WsmContext context, String source) {
        List<Object> forms = new Reader(source).readAll();
        return new BootstrapRootNode(context, forms).getCallTarget().call();
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
