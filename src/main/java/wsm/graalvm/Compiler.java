package wsm.graalvm;

import com.oracle.truffle.api.frame.FrameDescriptor;
import wsm.graalvm.Reader.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * Reader forms -> Truffle AST.
 *
 * Compile-time state contains only registry facts and lexical slot addresses.
 * Runtime Lisp values live in GlobalBindings or Truffle frames.
 */
public final class Compiler {

    private static final String ID_QUOTE = "0001";
    private static final String ID_ATOM = "0002";
    private static final String ID_EQ = "0003";
    private static final String ID_CONS = "0004";
    private static final String ID_CAR = "0005";
    private static final String ID_CDR = "0006";
    private static final String ID_COND = "0007";

    private static final String ID_LAMBDA = "0010";
    private static final String ID_DEFINE = "0011";
    private static final String ID_DEFMACRO = "0012";
    private static final String ID_DEF_COMPAT = "1000";

    private final WsmLanguage language;
    private final CanonRegistry registry;
    private final GlobalBindings globals;
    private final LexicalScope root;

    public Compiler(CanonRegistry registry) {
        this(registry, null, new GlobalBindings());
    }

    Compiler(CanonRegistry registry, WsmLanguage language, GlobalBindings globals) {
        this.language = language;
        this.registry = registry;
        this.globals = globals;
        this.root = LexicalScope.root(globals);
    }

    public LexicalScope root() {
        return root;
    }

    public List<WsmNode> compileProgram(List<Object> forms) {
        List<WsmNode> out = new ArrayList<>();
        for (Object form : forms) {
            out.add(compile(form, root));
        }
        return out;
    }

    public WsmNode compile(Object form, LexicalScope scope) {
        if (form instanceof Value.Pair p) return compileList(p, scope);
        if (form instanceof Value.Symbol s) return symbolNode(s.name, scope);
        if (form instanceof Value.StringValue || form instanceof Value.NumberValue) {
            return new WsmNode.ConstantNode(form);
        }
        if (form instanceof Token token) return symbolNode(token.spelling(), scope);
        if (form == Value.NIL) return new WsmNode.ConstantNode(Value.NIL);
        throw new WsmError(
                WsmError.Kind.INVALID_FORM,
                "unexpected reader form");
    }

    private WsmNode symbolNode(String spelling, LexicalScope scope) {
        String id = registry.semanticIdForToken(spelling);

        // Contract 6.0: Canon 0+7 identity wins before any lexical binding.
        if (id != null && isCanonPrimitive(id)) {
            if (isCanonSyntax(id)) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "canonical special form is syntax-only: " + id);
            }
            if (SemanticMechanismTable.supports(id)) {
                return new WsmNode.ConstantNode(new Value.SemanticRef(id));
            }
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "canonical identity has no substrate mechanism: " + id);
        }

        // Ordinary/non-Canon names remain lexically shadowable.
        LexicalScope.Binding local = scope.resolveLocal(spelling);
        if (local != null) {
            return new WsmNode.LocalReadNode(
                    local.depth(), local.slot(), spelling);
        }
        if (globals.isDeclared(spelling)) {
            return new WsmNode.GlobalReadNode(spelling, globals);
        }

        if (id != null) {
            if (isSpecial(id)) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "special form is syntax-only: " + id);
            }
            // Preserve upstream identity through compilation even when this
            // substrate has not materialized the corresponding mechanism yet.
            return new WsmNode.ConstantNode(new Value.SemanticRef(id));
        }

        return new WsmNode.GlobalReadNode(spelling, globals);
    }

    private WsmNode compileList(Object form, LexicalScope scope) {
        List<Object> items = items(form);
        if (items.isEmpty()) {
            return new WsmNode.ConstantNode(Value.NIL);
        }

        Object head = items.get(0);
        if (head == Reader.QUOTE_HEAD) {
            return dispatchSemanticHead(
                    ID_QUOTE,
                    items.subList(1, items.size()),
                    scope);
        }

        List<Object> args = items.subList(1, items.size());
        String spelling;
        if (head instanceof Token token) {
            spelling = token.spelling();
        } else if (head instanceof Value.Symbol symbol) {
            // Macro expansion produces ordinary Lisp data symbols. Resolve
            // their semantic identity exactly as source tokens, without
            // turning syntax heads into computed calls.
            spelling = symbol.name;
        } else {
            return new WsmNode.CallNode(
                    compile(head, scope),
                    compileAll(args, scope));
        }
        if (globals.isMacro(spelling)) {
            Object[] syntaxArgs = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                syntaxArgs[i] = ReaderDatum.toValue(args.get(i));
            }
            Object expanded = globals.macro(spelling).call(syntaxArgs);
            return compile(expanded, scope);
        }

        String id = registry.semanticIdForToken(spelling);

        // Canon resolution is immutable and precedes lexical lookup.
        if (id != null && isCanonPrimitive(id)) {
            return dispatchSemanticHead(id, args, scope);
        }

        // Non-Canon names are ordinary lexical names when bound.
        if (scope.resolveLocal(spelling) != null || globals.isDeclared(spelling)) {
            return new WsmNode.CallNode(
                    symbolNode(spelling, scope),
                    compileAll(args, scope));
        }

        if (id != null) {
            return dispatchSemanticHead(id, args, scope);
        }

        return new WsmNode.CallNode(
                symbolNode(spelling, scope),
                compileAll(args, scope));
    }

    private WsmNode dispatchSemanticHead(
            String id,
            List<Object> args,
            LexicalScope scope) {
        return switch (id) {
            case ID_QUOTE -> {
                if (args.size() != 1) {
                    throw new WsmError(
                            WsmError.Kind.ARITY,
                            ID_QUOTE + " expects 1 argument");
                }
                yield new WsmNode.QuoteNode(
                        ReaderDatum.toValue(args.get(0)));
            }
            case ID_LAMBDA -> compileLambda(args, scope);
            case ID_DEFINE, ID_DEF_COMPAT -> compileDefine(args, scope);
            case ID_DEFMACRO -> compileDefmacro(args, scope);
            case ID_COND -> compileCond(args, scope);
            default -> {
                if (SemanticMechanismTable.supports(id)) {
                    yield new WsmNode.CallNode(
                            new WsmNode.ConstantNode(new Value.SemanticRef(id)),
                            compileAll(args, scope));
                }
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "unroutable semantic id " + id);
            }
        };
    }

    private WsmNode compileLambda(
            List<Object> args,
            LexicalScope parentScope) {
        if (args.size() < 2) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_LAMBDA + " expects params and body");
        }

        LambdaParams params = lambdaParams(args.get(0));
        LexicalScope lambdaScope = parentScope.child();
        int[] slots = new int[params.fixedNames().size()];
        int restSlot = -1;

        for (int i = 0; i < params.fixedNames().size(); i++) {