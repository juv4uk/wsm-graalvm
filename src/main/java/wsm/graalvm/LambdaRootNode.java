package wsm.graalvm;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.RepeatingNode;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Executable root for one lambda body.
 *
 * Call convention:
 *   argument[0] = captured parent MaterializedFrame, or null
 *   argument[1..] = Lisp positional arguments
 *
 * Self-tail requests are consumed by a Truffle LoopNode. The loop is allowed
 * to reuse this frame only while the frame has not been materialized by a
 * nested closure.
 */
final class LambdaRootNode extends RootNode {
    private final int tailArgsSlot;
    private final int tailResultSlot;
    @Child private LoopNode loop;

    LambdaRootNode(
            WsmLanguage language,
            FrameDescriptor descriptor,
            int[] parameterSlots,
            int restSlot,
            int captureFlagSlot,
            int tailArgsSlot,
            int tailResultSlot,
            WsmNode[] body) {
        super(language, descriptor);
        this.tailArgsSlot = tailArgsSlot;
        this.tailResultSlot = tailResultSlot;
        this.loop = Truffle.getRuntime().createLoopNode(
                new LambdaRepeatingNode(
                        parameterSlots,
                        restSlot,
                        captureFlagSlot,
                        tailArgsSlot,
                        tailResultSlot,
                        body));
    }

    @Override
    public Object execute(VirtualFrame frame) {
        Object[] raw = frame.getArguments();
        Object[] initialArgs = new Object[Math.max(0, raw.length - 1)];
        if (initialArgs.length > 0) {
            System.arraycopy(raw, 1, initialArgs, 0, initialArgs.length);
        }
        frame.setObject(tailArgsSlot, initialArgs);
        frame.setObject(tailResultSlot, Value.UNBOUND);

        loop.execute(frame);

        Object result = frame.getValue(tailResultSlot);
        if (result == Value.UNBOUND) {
            throw new IllegalStateException("lambda loop exited without a result");
        }
        return result;
    }

    @Override
    public String getName() {
        return "wsm-lambda";
    }

    private static final class LambdaRepeatingNode extends Node implements RepeatingNode {
        private final int[] parameterSlots;
        private final int restSlot;
        private final int captureFlagSlot;
        private final int tailArgsSlot;
        private final int tailResultSlot;
        @Children private final WsmNode[] body;

        LambdaRepeatingNode(
                int[] parameterSlots,
                int restSlot,
                int captureFlagSlot,
                int tailArgsSlot,
                int tailResultSlot,
                WsmNode[] body) {
            this.parameterSlots = parameterSlots;
            this.restSlot = restSlot;
            this.captureFlagSlot = captureFlagSlot;
            this.tailArgsSlot = tailArgsSlot;
            this.tailResultSlot = tailResultSlot;
            this.body = body;
        }

        @Override
        public boolean executeRepeating(VirtualFrame frame) {
            Object pending = frame.getValue(tailArgsSlot);
            if (!(pending instanceof Object[] args)) {
                throw new IllegalStateException("lambda loop missing pending arguments");
            }
            bindArguments(frame, args);

            Object last = Value.NIL;
            for (WsmNode node : body) {
                last = node.executeGeneric(frame);
            }

            if (last instanceof WsmNode.TailCallRequest request) {
                Object[] frameArgs = frame.getArguments();
                Object capturedParent = frameArgs.length == 0 ? null : frameArgs[0];

                boolean sameTarget =
                        request.closure.target() == getRootNode().getCallTarget();
                boolean sameCapturedParent =
                        request.closure.captured() == capturedParent;
                boolean frameEscaped =
                        frame.getValue(captureFlagSlot) == Boolean.TRUE;

                if (sameTarget && sameCapturedParent && !frameEscaped) {
                    frame.setObject(tailArgsSlot, request.args);
                    return true;
                }

                // Not a safe self-tail reuse: preserve the pre-prototype call
                // path exactly.
                last = request.closure.call(request.args);
            }

            frame.setObject(tailResultSlot, last);
            return false;
        }

        private void bindArguments(VirtualFrame frame, Object[] args) {
            int received = args.length;
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
                frame.setObject(parameterSlots[i], args[i]);
            }
            if (restSlot >= 0) {
                java.util.List<Object> rest = new java.util.ArrayList<>();
                for (int i = parameterSlots.length; i < args.length; i++) {
                    rest.add(args[i]);
                }
                frame.setObject(restSlot, Value.list(rest));
            }
        }
    }
}
