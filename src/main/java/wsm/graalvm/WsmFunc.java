package wsm.graalvm;

/** A callable first-class mechanism. Canon primitives and closures share this shape. */
public interface WsmFunc {
    Object call(Object[] args);
}
