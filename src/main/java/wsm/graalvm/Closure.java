package wsm.graalvm;

/**
 * Closure value: params + raw body datum + captured lexical frame.
 * Time inside this M0 is compile-per-call — the formal Truffle
 * frames migration is issue #6 and remains owner-gated.
 */
public final class Closure implements WsmFunc, com.oracle.truffle.api.interop.TruffleObject {
    private final String[] params;
    private final Object[] bodyDatum;
    private final Environment captured;
    private final Compiler compiler;

    public Closure(String[] params, Object[] bodyDatum, Environment captured, Compiler compiler) {
        this.params = params;
        this.bodyDatum = bodyDatum;
        this.captured = captured;
        this.compiler = compiler;
    }

    @Override public Object call(Object[] args) {
        if (args.length != params.length)
            throw new WsmError(WsmError.Kind.ARITY, "lambda expects "
                    + params.length + " argument(s), received " + args.length);
        Environment local = new Environment(captured);
        for (int i = 0; i < params.length; i++) local.define(params[i], args[i]);
        Object last = Value.NIL;
        for (Object datum : bodyDatum) {
            WsmNode node = compiler.compile(datum, local);
            last = node.executeGeneric(null);
        }
        return last;
    }
}
