package wsm.graalvm;

import com.oracle.truffle.api.interop.TruffleObject;

/** A callable first-class runtime value. */
public interface WsmFunc extends TruffleObject {
    Object call(Object[] args);
}
