package wsm.graalvm;

import wsm.graalvm.Reader.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * M0 compiler: reader forms -> Truffle AST nodes. Canon resolution happens
 * BEFORE lexical lookup (contract 6.0), keyed by numeric semantic IDs read
 * from the registry. Code never mentions Lisp words — only ids.
 */
public final class Compiler {

    private final Environment root;
    private final CanonRegistry registry;
    private static final String ID_QUOTE = "0001";
    private static final String ID_COND = "0007";
    private static final String ID_LAMBDA = "0010";
    private static final String ID_DEFINE = "0011";
    private static final String ID_DEFMACRO = "0012";
    private static final String ID_DEF_LEGACY = "1000"; // compatibility-only alias of 0011

    public Compiler(CanonRegistry registry) {
        this.registry = registry;
        this.root = new Environment(null);
        root.define("t", Value.symbol("t"));
    }

    public Environment root() { return root; }

    public List<WsmNode> compileProgram(List<Object> forms) {
        List<WsmNode> out = new ArrayList<>();
        for (Object form : forms) out.add(compile(form, root));
        return out;
    }

    public WsmNode compile(Object form, Environment env) {
        if (form instanceof Value.Pair p) return compileList(p, env);
        if (form instanceof Long l) return new WsmNode.ConstantNode(l);
        if (form instanceof Value.Str st) return new WsmNode.ConstantNode(st);
        if (form instanceof Token token) return symbolNode(token.spelling(), env);
        if (form == Value.NIL) return new WsmNode.ConstantNode(Value.NIL);
        throw new WsmError(WsmError.Kind.INVALID_FORM, "unexpected reader form");
    }

    private WsmNode symbolNode(String spelling, Environment env) {
        String id = registry.idForSpelling(spelling);
        if (id == null) return new WsmNode.SymbolNode(spelling, env);
        if (isSpecial(id))
            throw new WsmError(WsmError.Kind.INVALID_FORM,
                    "canonical special form is syntax-only: " + spelling);
        if (CanonBuiltins.CALLABLE.contains(id))
            return new WsmNode.ConstantNode(CanonBuiltins.forId(id));
        return new WsmNode.SymbolNode(spelling, env);
    }

    private static boolean isSpecial(String id) {
        return ID_QUOTE.equals(id) || ID_COND.equals(id) || ID_LAMBDA.equals(id)
                || ID_DEFINE.equals(id) || ID_DEF_LEGACY.equals(id) || ID_DEFMACRO.equals(id);
    }

    private WsmNode compileList(Object form, Environment env) {
        // apostrophe sugar after list-wrap: form = (QUOTE_HEAD . datum)
        if (form instanceof Value.Pair qPair && qPair.car == Reader.QUOTE_HEAD)
            return new WsmNode.QuoteNode(datumValue(qPair.cdr));
        List<Object> items = items(form);
        if (items.isEmpty())
            return new WsmNode.ConstantNode(Value.NIL);

        Object head = items.get(0);
        List<Object> args = items.subList(1, items.size());

        if (!(head instanceof Token ht)) {
            WsmNode fn = compile(head, env);
            return new WsmNode.CallNode(fn, compileAll(args, env));
        }

        String id = registry.idForSpelling(ht.spelling());
        if (id == null) {
            WsmNode fn = compile(head, env);
            return new WsmNode.CallNode(fn, compileAll(args, env));
        }
        return dispatchSpecial(id, args, env);
    }



    private WsmNode quoteForm(List<Object> argForms) {
        if (argForms.size() != 1)
            throw new WsmError(WsmError.Kind.ARITY, ID_QUOTE + " expects exactly one argument");
        return new WsmNode.QuoteNode(argForms.get(0));
    }

    private WsmNode dispatchSpecial(String id, List<Object> args, Environment env) {
        return switch (id) {
            case ID_QUOTE -> {
                if (args.size() != 1)
                    throw new WsmError(WsmError.Kind.ARITY, ID_QUOTE + " expects 1 argument");
                yield new WsmNode.QuoteNode(datumValue(args.get(0)));
            }
            case ID_LAMBDA -> compileLambda(args, env);
            case ID_DEFINE, ID_DEF_LEGACY -> compileDefine(args, env);
            case ID_DEFMACRO -> throw new WsmError(WsmError.Kind.INVALID_FORM,
                    "0012 is not materialized in substrate M0");
            case ID_COND -> compileCond(args, env);
            default -> {
                if (CanonBuiltins.CALLABLE.contains(id)) {
                    yield new WsmNode.CallNode(
                            new WsmNode.ConstantNode(CanonBuiltins.forId(id)), compileAll(args, env));
                }
                throw new WsmError(WsmError.Kind.INVALID_FORM, "unroutable id " + id);
            }
        };
    }

    private WsmNode compileLambda(List<Object> args, Environment env) {
        if (args.size() < 2)
            throw new WsmError(WsmError.Kind.ARITY, ID_LAMBDA + " expects params and body");
        List<String> names = new ArrayList<>();
        for (Object pRaw : items(args.get(0))) {
            if (!(pRaw instanceof Token t))
                throw new WsmError(WsmError.Kind.INVALID_FORM, ID_LAMBDA + " binder must be a symbol");
            Token binder = (Token) pRaw;
            String binderId = registry.idForSpelling(binder.spelling());
            if (binderId != null)
                throw new WsmError(WsmError.Kind.INVALID_FORM,
                        "canonical name is immutable (binder refused): " + binder.spelling());
            names.add(binder.spelling());
        }
        List<Object> bodyDatum = args.subList(1, args.size());
        return new WsmNode.LambdaNode(names.toArray(String[]::new),
                bodyDatum.toArray(new Object[0]), env, this);
    }

    private WsmNode compileDefine(List<Object> args, Environment env) {
        if (args.size() != 2)
            throw new WsmError(WsmError.Kind.ARITY, ID_DEFINE + " expects 2 arguments");
        if (!(args.get(0) instanceof Token nameToken))
            throw new WsmError(WsmError.Kind.INVALID_FORM, ID_DEFINE + " binder must be a symbol");
        String binderId = registry.idForSpelling(nameToken.spelling());
        if (binderId != null)
            throw new WsmError(WsmError.Kind.INVALID_FORM,
                    "canonical name is immutable (binder refused): " + nameToken.spelling());
        return new WsmNode.DefineNode(nameToken.spelling(), compile(args.get(1), env), env);
    }

    /**
     * #217 dispatch: 3-part clause = (test expected-result-record body) — the
     * expected result is DATA compared structurally; 2-part clause =
     * (test body) — the historical migration bridge, ported 1:1.
     */
    private WsmNode compileCond(List<Object> clauses, Environment env) {
        List<WsmNode> tests = new ArrayList<>(), expecteds = new ArrayList<>(), bodies = new ArrayList<>();
        boolean[] migration = new boolean[clauses.size()];
        int i = 0;
        for (Object clause : clauses) {
            List<Object> parts = items(clause);
            switch (parts.size()) {
                case 3 -> {
                    tests.add(compile(parts.get(0), env));
                    expecteds.add(new WsmNode.ConstantNode(datumValue(parts.get(1))));
                    bodies.add(compile(parts.get(2), env));
                }
                case 2 -> {
                    tests.add(compile(parts.get(0), env));
                    expecteds.add(WsmNode.MigrationTruthyNode.INSTANCE);
                    bodies.add(compile(parts.get(1), env));
                    migration[i] = true;
                }
                default -> throw new WsmError(WsmError.Kind.INVALID_FORM,
                        ID_COND + " expects (query expected-result expression) or (test expression) clauses");
            }
            i++;
        }
        return new WsmNode.CondNode(tests.toArray(WsmNode[]::new),
                expecteds.toArray(WsmNode[]::new), bodies.toArray(WsmNode[]::new), migration);
    }

    private WsmNode[] compileAll(List<Object> argForms, Environment env) {
        WsmNode[] out = new WsmNode[argForms.size()];
        for (int i = 0; i < argForms.size(); i++) out[i] = compile(argForms.get(i), env);
        return out;
    }

    static List<Object> items(Object form) {
        List<Object> out = new ArrayList<>();
        Object cur = form;
        while (cur instanceof Value.Pair p) {
            out.add(p.car);
            cur = p.cdr;
        }
        if (cur != Value.NIL)
            throw new WsmError(WsmError.Kind.INVALID_FORM,
                    "a dotted pair is not executable code: " + Printer.print(form));
        return out;
    }

    /**
     * #217 expected-result datum: same value shape the test produces —
     * Tokens become Symbols, exact ints stay exact, nested structure
     * stays Pairs.
     */
    private static Object datumValue(Object form) {
        if (form instanceof Token t) return Value.symbol(t.spelling());
        if (form instanceof Long l) return l;
        if (form == Value.NIL) return Value.NIL;
        if (form instanceof Value.Pair p)
            return new Value.Pair(datumValue(p.car), datumValue(p.cdr));
        throw new WsmError(WsmError.Kind.INVALID_FORM, "unexpected datum");
    }
}
