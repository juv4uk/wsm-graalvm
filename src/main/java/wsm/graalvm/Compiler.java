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

    private static final Sid8 ID_00000001 = Sid8.bits(0,0,0,0,0,0,0,1);
    private static final Sid8 ID_00000010 = Sid8.bits(0,0,0,0,0,0,1,0);
    private static final Sid8 ID_00000011 = Sid8.bits(0,0,0,0,0,0,1,1);
    private static final Sid8 ID_00000100 = Sid8.bits(0,0,0,0,0,1,0,0);
    private static final Sid8 ID_00000101 = Sid8.bits(0,0,0,0,0,1,0,1);
    private static final Sid8 ID_00000110 = Sid8.bits(0,0,0,0,0,1,1,0);
    private static final Sid8 ID_00000111 = Sid8.bits(0,0,0,0,0,1,1,1);

    private static final Sid8 ID_00001000 = Sid8.bits(0,0,0,0,1,0,0,0);
    private static final Sid8 ID_00001001 = Sid8.bits(0,0,0,0,1,0,0,1);
    private static final Sid8 ID_00001011 = Sid8.bits(0,0,0,0,1,0,1,1);
    private static final Sid8 ID_01001101 = Sid8.bits(0,1,0,0,1,1,0,1);

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
        Sid8 id = registry.semanticIdForToken(spelling);

        // Reserved function SID resolution precedes lexical binding.
        if (id != null && isReservedPrimitiveSid(id)) {
            if (isSyntaxSid(id)) {
                throw new WsmError(
                        WsmError.Kind.INVALID_FORM,
                        "reserved syntax SID is syntax-only: " + id);
            }
            if (SemanticMechanismTable.supports(id)) {
                return new WsmNode.ConstantNode(new Value.SemanticRef(id));
            }
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "reserved SID has no substrate mechanism: " + id);
        }

        // Ordinary unresolved names remain lexically shadowable.
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
        List<Object> items = items(form);
        if (items.isEmpty()) {
            return new WsmNode.ConstantNode(Value.NIL);
        }

        Object head = items.get(0);
        if (head == Reader.QUOTE_HEAD) {
            return dispatchSemanticHead(
                    ID_00000001,
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
        } else if (head instanceof Value.SemanticRef semanticHead) {
            return dispatchSemanticHead(semanticHead.id(), args, scope);
        } else if (head instanceof Value.NumberValue) {
            // Numeric values are ordinary data and can never mint Sid8.
            return new WsmNode.CallNode(
                    compile(head, scope),
                    compileAll(args, scope));
        } else {
            return new WsmNode.CallNode(
                    compile(head, scope),
                    compileAll(args, scope));
        }
        Sid8 id = registry.semanticIdForToken(spelling);

        // Macro dispatch is identity-first. The exact Sid8 key is authoritative;
        // admitted human surfaces are aliases at the reader boundary only.
        GlobalBindings.MacroValue macro = null;
        if (id != null && globals.isMacro(id)) {
            macro = globals.macro(id);
        } else if (globals.isMacro(spelling)) {
            macro = globals.macro(spelling);
        }
        if (macro != null) {
            Object[] syntaxArgs = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                syntaxArgs[i] = ReaderDatum.toValue(args.get(i));
            }
            Object expanded = macro.expand(syntaxArgs);
            return compile(expanded, scope);
        }

        // Reserved SID resolution precedes lexical lookup.
        if (id != null && isReservedPrimitiveSid(id)) {
            return dispatchSemanticHead(id, args, scope);
        }

        // Unresolved names are ordinary lexical names when bound.
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
            Sid8 id,
            List<Object> args,
            LexicalScope scope) {
        if (ID_00000001.equals(id)) {
            if (args.size() != 1) {
                throw new WsmError(
                        WsmError.Kind.ARITY,
                        ID_00000001 + " expects 1 argument");
            }
            return new WsmNode.QuoteNode(
                    ReaderDatum.toValue(args.get(0)));
        }
        if (ID_00001000.equals(id)) return compileLambda(args, scope);
        if (ID_00001001.equals(id) || ID_00001011.equals(id)) return compileDefine(args, scope);
        if (ID_00000111.equals(id)) return compileCond(args, scope);
        if (ID_01001101.equals(id)) return compileEval(args, scope);

        // The exact Sid8 stays the function key. Runtime mechanism selection
        // receives the same Sid8 and has no name/string fallback.
        return new WsmNode.CallNode(
                new WsmNode.ConstantNode(new Value.SemanticRef(id)),
                compileAll(args, scope));
    }

    private WsmNode compileLambda(
            List<Object> args,
            LexicalScope parentScope) {
        if (args.size() < 2) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_00001000 + " expects params and body");
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

    private WsmNode compileDefine(
            List<Object> args,
            LexicalScope scope) {
        if (args.size() != 2) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_00001001 + " expects 2 arguments");
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
                ID_00001001 + " binder must be a symbol");
    }

    private void ensureBinderAllowed(String spelling) {
        Sid8 id = registry.semanticIdForToken(spelling);
        if (id != null && isReservedPrimitiveSid(id)) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "reserved function spelling is immutable (binder refused): "
                            + spelling);
        }
    }

    private static boolean isReservedPrimitiveSid(Sid8 id) {
        return ID_00000001.equals(id)
                || ID_00000010.equals(id)
                || ID_00000011.equals(id)
                || ID_00000100.equals(id)
                || ID_00000101.equals(id)
                || ID_00000110.equals(id)
                || ID_00000111.equals(id);
    }

    private static boolean isSyntaxSid(Sid8 id) {
        return ID_00000001.equals(id) || ID_00000111.equals(id);
    }

    private static boolean isSpecial(Sid8 id) {
        return isSyntaxSid(id)
                || ID_00001000.equals(id)
                || ID_00001001.equals(id)
                || ID_00001011.equals(id);
    }


    private WsmNode compileEval(
            List<Object> args,
            LexicalScope scope) {
        if (args.size() != 1) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    ID_01001101 + " expects 1 argument");
        }
        return new WsmNode.EvalNode(
                compile(args.get(0), scope),
                this,
                scope);
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
                    ID_00000111
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
                        ID_00001000 + " binder must be a symbol");
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
                        ID_00001000 + " dotted rest binder must be a symbol");
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
