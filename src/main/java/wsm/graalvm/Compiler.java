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
    private final SemanticResolver resolver;
    private static final String ID_QUOTE = "0001";
    private static final String ID_COND = "0007";
    private static final String ID_LAMBDA = "0010";
    private static final String ID_DEFINE = "0011";
    private static final String ID_DEFMACRO = "0012";

    public Compiler(CanonRegistry registry) {
        this.registry = registry;
        this.resolver = new SemanticResolver(registry);
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
        if (form instanceof Token token) return symbolNode(token.spelling(), env);
        if (form == Value.NIL) return new WsmNode.ConstantNode(Value.NIL);
        throw new WsmError(WsmError.Kind.INVALID_FORM, "unexpected reader form");
    }

    private WsmNode symbolNode(String spelling, Environment env) {
        SemanticResolver.Resolution resolved = resolver.resolve(new Token(spelling));
        if (resolved instanceof SemanticResolver.Lexical lexical) {
            return new WsmNode.SymbolNode(lexical.spelling(), env);
        }

        String id = ((SemanticResolver.Semantic) resolved).id();
        if (isSpecial(id))
            throw new WsmError(WsmError.Kind.INVALID_FORM,
                    "semantic special form is syntax-only: " + id);
        if (SemanticMechanismTable.supports(id))
            return new WsmNode.ConstantNode(new Value.SemanticRef(id));

        // A registry identity can exist without being materialized by this
        // substrate yet. Never fall back to treating its source spelling as a
        // lexical symbol: that would reintroduce surface authority.
        throw new WsmError(WsmError.Kind.INVALID_FORM,
                "semantic identity has no substrate value mechanism: " + id);
    }

    private static boolean isSpecial(String id) {
        return ID_QUOTE.equals(id) || ID_COND.equals(id) || ID_LAMBDA.equals(id)
                || ID_DEFINE.equals(id) || ID_DEFMACRO.equals(id);
    }

    private WsmNode compileList(Object form, Environment env) {
        List<Object> items = items(form);
        if (items.isEmpty())
            return new WsmNode.ConstantNode(Value.NIL);

        Object head = items.get(0);
        if (head == Reader.QUOTE_HEAD)
            return quoteForm(items.subList(1, items.size()));
        List<Object> args = items.subList(1, items.size());

        if (!(head instanceof Token ht)) {
            WsmNode fn = compile(head, env);
            return new WsmNode.CallNode(fn, compileAll(args, env));
        }

        SemanticResolver.Resolution resolvedHead = resolver.resolve(ht);
        if (resolvedHead instanceof SemanticResolver.Lexical) {
            WsmNode fn = compile(head, env);
            return new WsmNode.CallNode(fn, compileAll(args, env));
        }

        String id = ((SemanticResolver.Semantic) resolvedHead).id();
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
                yield new WsmNode.QuoteNode(args.get(0));
            }
            case ID_LAMBDA -> compileLambda(args, env);
            case ID_DEFINE -> compileDefine(args, env);
            case ID_DEFMACRO -> throw new WsmError(WsmError.Kind.INVALID_FORM,
                    "0012 is not materialized in substrate M0");
            case ID_COND -> compileCond(args, env);
            default -> {
                if (SemanticMechanismTable.supports(id)) {
                    yield new WsmNode.CallNode(
                            new WsmNode.ConstantNode(new Value.SemanticRef(id)), compileAll(args, env));
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
            String binderId = registry.semanticIdForToken(binder.spelling());
            if (binderId != null)
                throw new WsmError(WsmError.Kind.INVALID_FORM,
                        "canonical name is immutable (binder refused): " + binder.spelling());
            names.add(binder.spelling());
        }
        List<WsmNode> body = new ArrayList<>();
        for (Object b : args.subList(1, args.size())) body.add(compile(b, env));
        return new WsmNode.LambdaNode(names.toArray(String[]::new), body.toArray(WsmNode[]::new), env);
    }

    private WsmNode compileDefine(List<Object> args, Environment env) {
        if (args.size() != 2)
            throw new WsmError(WsmError.Kind.ARITY, ID_DEFINE + " expects 2 arguments");
        if (!(args.get(0) instanceof Token nameToken))
            throw new WsmError(WsmError.Kind.INVALID_FORM, ID_DEFINE + " binder must be a symbol");
        String binderId = registry.semanticIdForToken(nameToken.spelling());
        if (binderId != null)
            throw new WsmError(WsmError.Kind.INVALID_FORM,
                    "canonical name is immutable (binder refused): " + nameToken.spelling());
        return new WsmNode.DefineNode(nameToken.spelling(), compile(args.get(1), env), env);
    }

    /**
     * #217 canonical dispatch only.
     *
     * A clause is (query expected-result expression). The query is evaluated,
     * the expected result is materialized as Lisp DATA (never executed), and
     * the two values are compared structurally. No generic truth coercion
     * exists on this path. Historical two-part cond is compatibility debt and
     * deliberately does not define this substrate's core semantics.
     */
    private WsmNode compileCond(List<Object> clauses, Environment env) {
        List<WsmNode> tests = new ArrayList<>();
        List<WsmNode> expecteds = new ArrayList<>();
        List<WsmNode> bodies = new ArrayList<>();

        for (Object clause : clauses) {
            List<Object> parts = items(clause);
            if (parts.size() != 3) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        ID_COND + " expects canonical (query expected-result expression) clauses");
            }

            tests.add(compile(parts.get(0), env));
            expecteds.add(new WsmNode.ConstantNode(ReaderDatum.toValue(parts.get(1))));
            bodies.add(compile(parts.get(2), env));
        }

        return new WsmNode.CondNode(
                tests.toArray(WsmNode[]::new),
                expecteds.toArray(WsmNode[]::new),
                bodies.toArray(WsmNode[]::new));
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
            throw new WsmError(WsmError.Kind.INVALID_FORM, "a dotted pair is not executable code");
        return out;
    }
}
