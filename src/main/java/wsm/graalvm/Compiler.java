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
            if (SemanticMechanismTable.supports(id)) {
                return new WsmNode.ConstantNode(new Value.SemanticRef(id));
            }
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "semantic identity has no substrate value mechanism: " + id);
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
        if (!(head instanceof Token token)) {
            return new WsmNode.CallNode(
                    compile(head, scope),
                    compileAll(args, scope));
        }

        String spelling = token.spelling();
        if (globals.isMacro(spelling)) {
            Object expanded = MacroExpander.expand(
                    globals.macro(spelling),
                    args,
                    registry);
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
            case ID_DEFMACRO -> compileDefmacro(args);
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
            String binder = params.fixedNames().get(i);
            ensureBinderAllowed(binder);
            slots[i] = lambdaScope.declareLocal(binder);
        }
        if (params.restName() != null) {
            ensureBinderAllowed(params.restName());
            restSlot = lambdaScope.declareLocal(params.restName());
        }

        List<WsmNode> body = new ArrayList<>();
        for (Object form : args.subList(1, args.size())) {
            body.add(compile(form, lambdaScope));
        }

        FrameDescriptor descriptor = lambdaScope.finishFrame();
        LambdaRootNode lambdaRoot = new LambdaRootNode(
                language,
                descriptor,
                slots,
                restSlot,
                body.toArray(WsmNode[]::new));

        return new WsmNode.LambdaNode(
                lambdaRoot.getCallTarget(),
                !parentScope.isRoot());
    }

    private WsmNode compileDefmacro(List<Object> args) {
        if (args.size() < 3) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_DEFMACRO + " expects name, params and body");
        }
        if (!(args.get(0) instanceof Token nameToken)) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    ID_DEFMACRO + " binder must be a symbol");
        }

        String name = nameToken.spelling();
        ensureBinderAllowed(name);
        LambdaParams params = lambdaParams(args.get(1));
        globals.defineMacro(
                name,
                new MacroDefinition(
                        params.fixedNames(),
                        params.restName(),
                        List.copyOf(args.subList(2, args.size()))));
        return new WsmNode.ConstantNode(Value.NIL);
    }

    private WsmNode compileDefine(
            List<Object> args,
            LexicalScope scope) {
        if (args.size() != 2) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_DEFINE + " expects 2 arguments");
        }
        if (!(args.get(0) instanceof Token nameToken)) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    ID_DEFINE + " binder must be a symbol");
        }

        String name = nameToken.spelling();
        ensureBinderAllowed(name);

        if (scope.isRoot()) {
            // Declare before compiling the value so recursive top-level
            // closures resolve their own name through shared globals.
            globals.declare(name);
            return new WsmNode.GlobalDefineNode(
                    name,
                    compile(args.get(1), scope),
                    globals);
        }

        // Same rule for local recursive definitions: the frame slot exists
        // before the lambda value is compiled/captured.
        int slot = scope.declareLocal(name);
        return new WsmNode.LocalDefineNode(
                slot,
                compile(args.get(1), scope));
    }

    private void ensureBinderAllowed(String spelling) {
        String id = registry.semanticIdForToken(spelling);
        if (id != null && isCanonPrimitive(id)) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "Canon 0+7 spelling is immutable (binder refused): "
                            + spelling);
        }
    }

    private static boolean isCanonPrimitive(String id) {
        return ID_QUOTE.equals(id)
                || ID_ATOM.equals(id)
                || ID_EQ.equals(id)
                || ID_CONS.equals(id)
                || ID_CAR.equals(id)
                || ID_CDR.equals(id)
                || ID_COND.equals(id);
    }

    private static boolean isCanonSyntax(String id) {
        return ID_QUOTE.equals(id) || ID_COND.equals(id);
    }

    private static boolean isSpecial(String id) {
        return isCanonSyntax(id)
                || ID_LAMBDA.equals(id)
                || ID_DEFINE.equals(id)
                || ID_DEFMACRO.equals(id)
                || ID_DEF_COMPAT.equals(id);
    }

    private WsmNode compileCond(
            List<Object> clauses,
            LexicalScope scope) {
        List<WsmNode> tests = new ArrayList<>();
        List<WsmNode> expecteds = new ArrayList<>();
        List<WsmNode> bodies = new ArrayList<>();
        List<Boolean> truthiness = new ArrayList<>();

        for (Object clause : clauses) {
            List<Object> parts = items(clause);
            if (parts.size() == 2) {
                tests.add(compile(parts.get(0), scope));
                expecteds.add(new WsmNode.ConstantNode(Value.NIL));
                bodies.add(compile(parts.get(1), scope));
                truthiness.add(true);
                continue;
            }
            if (parts.size() == 3) {
                tests.add(compile(parts.get(0), scope));
                expecteds.add(new WsmNode.ConstantNode(
                        ReaderDatum.toValue(parts.get(1))));
                bodies.add(compile(parts.get(2), scope));
                truthiness.add(false);
                continue;
            }
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    ID_COND
                            + " expects canonical (query expected-result expression) "
                            + "or migration-only (test expression) clauses");
        }

        boolean[] legacyTruthiness = new boolean[truthiness.size()];
        for (int i = 0; i < truthiness.size(); i++) {
            legacyTruthiness[i] = truthiness.get(i);
        }
        return new WsmNode.CondNode(
                tests.toArray(WsmNode[]::new),
                expecteds.toArray(WsmNode[]::new),
                bodies.toArray(WsmNode[]::new),
                legacyTruthiness);
    }

    private record LambdaParams(
            List<String> fixedNames,
            String restName) {}

    private LambdaParams lambdaParams(Object raw) {
        if (raw instanceof Token token) {
            return new LambdaParams(List.of(), token.spelling());
        }

        List<String> fixedNames = new ArrayList<>();
        Object cur = raw;
        while (cur instanceof Value.Pair pair) {
            if (!(pair.car instanceof Token binder)) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        ID_LAMBDA + " binder must be a symbol");
            }
            fixedNames.add(binder.spelling());
            cur = pair.cdr;
        }

        String restName = null;
        if (cur != Value.NIL) {
            if (!(cur instanceof Token token)) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        ID_LAMBDA + " dotted rest binder must be a symbol");
            }
            restName = token.spelling();
        }
        return new LambdaParams(fixedNames, restName);
    }

    private WsmNode[] compileAll(
            List<Object> argForms,
            LexicalScope scope) {
        WsmNode[] out = new WsmNode[argForms.size()];
        for (int i = 0; i < argForms.size(); i++) {
            out[i] = compile(argForms.get(i), scope);
        }
        return out;
    }

    static List<Object> items(Object form) {
        List<Object> out = new ArrayList<>();
        Object cur = form;
        while (cur instanceof Value.Pair p) {
            out.add(p.car);
            cur = p.cdr;
        }
        if (cur != Value.NIL) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "a dotted pair is not executable code");
        }
        return out;
    }
}
