package wsm.graalvm;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.util.List;

/**
 * Executes one top-level source unit in source order.
 *
 * Each form is compiled only immediately before execution so Lisp-defined
 * bindings and macros created by earlier forms are visible to later forms.
 * This preserves bootstrap semantics without promoting derived Lisp operations
 * into host mechanisms merely to satisfy whole-file compilation.
 */
final class SequentialProgramBodyNode extends WsmNode {
    private final WsmLanguage language;
    private final WsmContext context;
    private final List<Object> forms;

    SequentialProgramBodyNode(
            WsmLanguage language,
            WsmContext context,
            List<Object> forms) {
        this.language = language;
        this.context = context;
        this.forms = List.copyOf(forms);
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object last = Value.NIL;
        for (Object form : forms) {
            Compiler compiler =
                    new Compiler(context.registry(), language, context.globals());
            WsmNode node = compiler.compile(form, compiler.root());
            last = node.executeGeneric(frame);
        }
        return last;
    }
}
