package wsm.graalvm;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;

/**
 * Runtime lambda closure = executable CallTarget + captured lexical parent.
 */
public final class Closure implements WsmFunc {
    private final CallTarget target;
    private final MaterializedFrame captured;

    public Closure(CallTarget target, MaterializedFrame captured) {
        this.target = target;
        this.captured = captured;
    }

    @Override
    public Object call(Object[] args) {
        Object[] callArgs = new Object[args.length + 1];
        callArgs[0] = captured;
        System.arraycopy(args, 0, callArgs, 1, args.length);
        return target.call(callArgs);
    }

    CallTarget target() {
        return target;
    }

    MaterializedFrame captured() {
        return captured;
    }
}
