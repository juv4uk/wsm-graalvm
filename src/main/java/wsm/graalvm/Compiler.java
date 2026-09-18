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
    private static final String ID_DEF_COMPAT = "1000";
    private static final String ID_EVAL = "1062";

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
            // Preserve upstream semantic identity through compilation even
            // when this substrate has not materialized its mechanism yet.
            return new WsmNode.ConstantNode(new Value.SemanticRef(id));
        }

        return new WsmNode.GlobalReadNode(spelling, globals);
    }

    private WsmNode compileList(Object form, LexicalScope scope) {
        return compileList(form, scope, false);
    }

    private WsmNode compileTail(Object form, LexicalScope scope) {
        if (form instanceof Value.Pair) {
            return compileList(form, scope, true);
        }
        return compile(form, scope);
    }

    private WsmNode callNode(
            WsmNode fn,
            WsmNode[] args,
            boolean tailPosition) {
        return tailPosition
                ? new WsmNode.TailCallNode(fn, args)
                : new WsmNode.CallNode(fn, args);
    }

    private WsmNode compileList(
            Object form,
            LexicalScope scope,
            boolean tailPosition) {
        List<Object> items = items(form);
        if (items.isEmpty()) {
            return new WsmNode.ConstantNode(Value.NIL);
        }

        Object head = items.get(0);
        if (head == Reader.QUOTE_HEAD) {
            return dispatchSemanticHead(
                    ID_QUOTE,
                    items.subList(1, items.size()),
                    scope,
                    tailPosition);
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
        } else if (head instanceof Value.NumberValue numericHead) {
            // Issue #47: numeric heads may still be admitted machine IDs.
            // The admitted set comes from the pinned registry only.
            String id = registry.semanticIdForNumeric(numericHead.numerator().longValueExact());
            if (id != null) {
                return dispatchSemanticHead(id, args, scope, tailPosition);
            }
            return callNode(
                    compile(head, scope),
                    compileAll(args, scope),
                    tailPosition);
        } else {
            return callNode(
                    compile(head, scope),
                    compileAll(args, scope),
                    tailPosition);
        }
        if (globals.isMacro(spelling)) {
            Object[] syntaxArgs = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                syntaxArgs[i] = ReaderDatum.toValue(args.get(i));
            }
            Object expanded = globals.macro(spelling).expand(syntaxArgs);
            return tailPosition
                    ? compileTail(expanded, scope)
                    : compile(expanded, scope);
        }

        String id = registry.semanticIdForToken(spelling);

        // Canon resolution is immutable and precedes lexical lookup.
        if (id != null && isCanonPrimitive(id)) {
            return dispatchSemanticHead(id, args, scope, tailPosition);
        }

        // Non-Canon names are ordinary lexical names when bound.
        if (scope.resolveLocal(spelling) != null || globals.isDeclared(spelling)) {
            return callNode(
                    symbolNode(spelling, scope),
                    compileAll(args, scope),
                    tailPosition);
        }

        if (id != null) {
            return dispatchSemanticHead(id, args, scope, tailPosition);
        }

        return callNode(
                symbolNode(spelling, scope),
                compileAll(args, scope),
                tailPosition);
    }

    private WsmNode dispatchSemanticHead(
            String id,
            List<Object> args,
            LexicalScope scope,
            boolean tailPosition) {
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
            case ID_COND -> compileCond(args, scope, tailPosition);
            case ID_EVAL -> compileEval(args, scope);
            default -> {
                // Preserve every admitted semantic identity as a first-class
                // callable reference. Mechanism availability is an invocation
                // concern, not a compile-time semantic admission rule.
                yield callNode(
                        new WsmNode.ConstantNode(new Value.SemanticRef(id)),
                        compileAll(args, scope),
                        tailPosition);
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

        List<Object> bodyForms = args.subList(1, args.size());
        List<WsmNode> body = new ArrayList<>();
        for (int i = 0; i < bodyForms.size(); i++) {
            Object form = bodyForms.get(i);
            body.add(i == bodyForms.size() - 1
                    ? compileTail(form, lambdaScope)
                    : compile(form, lambdaScope));
        }

        FrameDescriptor descriptor = lambdaScope.finishFrame();
        LambdaRootNode lambdaRoot = new LambdaRootNode(
                language,
                descriptor,
                slots,
                restSlot,
                lambdaScope.captureFlagSlot(),
                lambdaScope.tailArgsSlot(),
                lambdaScope.tailResultSlot(),
                body.toArray(WsmNode[]::new));

        return new WsmNode.LambdaNode(
                lambdaRoot.getCallTarget(),
                !parentScope.isRoot(),
                parentScope.isRoot() ? -1 : parentScope.captureFlagSlot());
    }

    private WsmNode compileDefine(
            List<Object> args,
            LexicalScope scope) {
        if (args.size() != 2) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_DEFINE + " expects 2 arguments");
        }
        String name = defineBinderName(args.get(0));
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

    /**
     * DEFINE may arrive directly from the reader or as Lisp data emitted by a
     * macro/eval path. Both representations denote the same Lisp Symbol.
     */
    private static String defineBinderName(Object binder) {
        if (binder instanceof Token token) {
            return token.spelling();
        }
        if (binder instanceof Value.Symbol symbol) {
            return symbol.name;
        }
        throw new WsmError(
                WsmError.Kind.INVALID_FORM,
                ID_DEFINE + " binder must be a symbol");
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
                || ID_DEF_COMPAT.equals(id);
    }


    private WsmNode compileEval(
            List<Object> args,
            LexicalScope scope) {
        if (args.size() != 1) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_EVAL + " expects 1 argument");
        }
        return new WsmNode.EvalNode(
                compile(args.get(0), scope),
                this,
                scope);
    }

    private WsmNode compileCond(
            List<Object> clauses,
            LexicalScope scope,
            boolean tailPosition) {
        List<WsmNode> tests = new ArrayList<>();
        List<WsmNode> expecteds = new ArrayList<>();
        List<WsmNode> bodies = new ArrayList<>();
        List<Boolean> truthiness = new ArrayList<>();

        for (Object clause : clauses) {
            List<Object> parts = items(clause);
            if (parts.size() == 2) {
                tests.add(compile(parts.get(0), scope));
                expecteds.add(new WsmNode.ConstantNode(Value.NIL));
                bodies.add(tailPosition
                        ? compileTail(parts.get(1), scope)
                        : compile(parts.get(1), scope));
                truthiness.add(true);
                continue;
            }
            if (parts.size() == 3) {
                tests.add(compile(parts.get(0), scope));
                expecteds.add(new WsmNode.ConstantNode(
                        ReaderDatum.toValue(parts.get(1))));
                bodies.add(tailPosition
                        ? compileTail(parts.get(2), scope)
                        : compile(parts.get(2), scope));
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
        String bare = binderSpelling(raw);
        if (bare != null) {
            return new LambdaParams(List.of(), bare);
        }

        List<String> fixedNames = new ArrayList<>();
        Object cur = raw;
        while (cur instanceof Value.Pair pair) {
            String binder = binderSpelling(pair.car);
            if (binder == null) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        ID_LAMBDA + " binder must be a symbol");
            }
            fixedNames.add(binder);
            cur = pair.cdr;
        }

        String restName = null;
        if (cur != Value.NIL) {
            restName = binderSpelling(cur);
            if (restName == null) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        ID_LAMBDA + " dotted rest binder must be a symbol");
            }
        }
        return new LambdaParams(fixedNames, restName);
    }

    private static String binderSpelling(Object value) {
        if (value instanceof Token token) return token.spelling();
        if (value instanceof Value.Symbol symbol) return symbol.name;
        return null;
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
