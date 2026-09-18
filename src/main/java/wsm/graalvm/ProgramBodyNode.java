package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.List;

final class ProgramBodyNode extends WsmNode {
    private final List<WsmNode> forms;

    ProgramBodyNode(List<WsmNode> forms) {
        this.forms = forms;
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
