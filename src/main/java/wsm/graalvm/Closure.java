package wsm.graalvm;

/** Closure value: params + body + captured frame (lexical, shared). */
public final class Closure implements WsmFunc {
    private final String[] params;
    private final WsmNode[] body;
    private final Environment captured;

    public Closure(String[] params, WsmNode[] body, Environment captured) {
        this.params = params;
        this.body = body;
        this.captured = captured;
    }

    @Override public Object call(Object[] args) {
        if (args.length != params.length)
            throw new WsmError(WsmError.Kind.ARITY, "0007-bound lambda expects "
                    + params.length + " argument(s), received " + args.length);
        Environment local = new Environment(captured);
        for (int i = 0; i < params.length; i++) local.define(params[i], args[i]);
        WsmNode[] compiled = body;
        Object last = Value.NIL;
        for (WsmNode node : compiledBody()) {
            last = node.executeGeneric(null);
        }
        return last;
    }

    private WsmNode[] compiledBody() {
        // Definitions inside a lambda body mutate the captured frame (shared lexical frame).
        return body;
    }
}
