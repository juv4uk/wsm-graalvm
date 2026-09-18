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
    private final int restSlot;
    @Children private final WsmNode[] body;

    LambdaRootNode(
            WsmLanguage language,
            FrameDescriptor descriptor,
            int[] parameterSlots,
            int restSlot,
            WsmNode[] body) {
        super(language, descriptor);
        this.parameterSlots = parameterSlots;
        this.restSlot = restSlot;
        this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        int received = Math.max(0, args.length - 1);
        if (restSlot < 0 && received != parameterSlots.length) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    "0010 lambda expects " + parameterSlots.length
                            + " argument(s), received " + received);
        }
        if (restSlot >= 0 && received < parameterSlots.length) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    "0010 lambda expects at least " + parameterSlots.length
                            + " argument(s), received " + received);
        }

        for (int i = 0; i < parameterSlots.length; i++) {
            frame.setObject(parameterSlots[i], args[i + 1]);
        }
        if (restSlot >= 0) {
            java.util.List<Object> rest = new java.util.ArrayList<>();
            for (int i = parameterSlots.length + 1; i < args.length; i++) {
                rest.add(args[i]);
            }
            frame.setObject(restSlot, Value.list(rest));
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
