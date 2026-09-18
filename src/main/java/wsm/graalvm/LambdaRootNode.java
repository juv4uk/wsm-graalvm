package wsm.graalvm;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Executable root for one lambda body.
 *
 * Call convention:
 *   argument[0] = captured parent MaterializedFrame, or null
 *   argument[1..] = Lisp positional arguments
 */
final class LambdaRootNode extends RootNode {
    private final int[] parameterSlots;
    @Children private final WsmNode[] body;

    LambdaRootNode(
            WsmLanguage language,
            FrameDescriptor descriptor,
            int[] parameterSlots,
            WsmNode[] body) {
        super(language, descriptor);
        this.parameterSlots = parameterSlots;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        int received = Math.max(0, args.length - 1);
        if (received != parameterSlots.length) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    "0010 lambda expects " + parameterSlots.length
                            + " argument(s), received " + received);
        }

        for (int i = 0; i < parameterSlots.length; i++) {
            frame.setObject(parameterSlots[i], args[i + 1]);
        }

        Object last = Value.NIL;
        for (WsmNode node : body) {
            last = node.executeGeneric(frame);
        }
        return last;
    }

    @Override
    public String getName() {
        return "wsm-lambda";
    }
}
