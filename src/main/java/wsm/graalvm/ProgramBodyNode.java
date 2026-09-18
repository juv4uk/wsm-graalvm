package wsm.graalvm;

import java.util.List;

final class ProgramBodyNode extends WsmNode {
    private final List<WsmNode> forms;
    ProgramBodyNode(List<WsmNode> forms) { this.forms = forms; }

    Object run() {
        Object last = Value.NIL;
        for (WsmNode form : forms) {
            last = form.executeGeneric(null);
        }
        return last;
    }

    @Override public Object executeGeneric(com.oracle.truffle.api.frame.VirtualFrame frame) { return run(); }
}
