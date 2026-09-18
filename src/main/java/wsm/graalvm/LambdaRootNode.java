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
        boolean variadic = restSlot >= 0;
        boolean wrongArity = variadic
                ? received < parameterSlots.length
                : received != parameterSlots.length;
        if (wrongArity) {
            String expectation = variadic
                    ? "at least " + parameterSlots.length
                    : Integer.toString(parameterSlots.length);
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    "0010 lambda expects " + expectation
                            + " argument(s), received " + received);
        }

        for (int i = 0; i < parameterSlots.length; i++) {
            frame.setObject(parameterSlots[i], args[i + 1]);
        }

        if (variadic) {
            Object rest = Value.NIL;
            for (int i = received - 1; i >= parameterSlots.length; i--) {
                rest = new Value.Pair(args[i + 1], rest);
            }
            frame.setObject(restSlot, rest);
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
