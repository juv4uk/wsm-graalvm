package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Children;
import java.util.List;

final class ProgramBodyNode extends WsmNode {
    @Children private final WsmNode[] forms;

    ProgramBodyNode(List<WsmNode> forms) {
        this.forms = forms.toArray(WsmNode[]::new);
    }

    Object run(VirtualFrame frame) {
        Object last = Value.NIL;
        for (WsmNode form : forms) {
            last = form.executeGeneric(frame);
        }
        return last;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return run(frame);
    }
}
