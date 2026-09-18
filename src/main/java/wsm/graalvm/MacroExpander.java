package wsm.graalvm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import wsm.graalvm.Reader.Token;

/**
 * Compile-time reduction of language-owned macros.
 *
 * Macro bodies receive unevaluated syntax data. Expansion uses the same
 * numeric semantic mechanisms as ordinary execution for quote/cons/car/cdr.
 * It never dispatches by an English or Ukrainian spelling.
 */
final class MacroExpander {
    private static final String ID_QUOTE = "0001";
    private static final String ID_CONS = "0004";
    private static final String ID_CAR = "0005";
    private static final String ID_CDR = "0006";

    private MacroExpander() {}

    static Object expand(
            MacroDefinition macro,
            List<Object> rawArgs,
            CanonRegistry registry) {
        Map<String, Object> bindings = new HashMap<>();

        int fixed = macro.fixedNames().size();
        if (macro.restName() == null && rawArgs.size() != fixed) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    "macro expects " + fixed + " argument(s), received "
                            + rawArgs.size());
        }
        if (macro.restName() != null && rawArgs.size() < fixed) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    "macro expects at least " + fixed
                            + " argument(s), received " + rawArgs.size());
        }

        for (int i = 0; i < fixed; i++) {
            bindings.put(
                    macro.fixedNames().get(i),
                    ReaderDatum.toValue(rawArgs.get(i)));
        }
        if (macro.restName() != null) {
            List<Object> rest = new java.util.ArrayList<>();
            for (int i = fixed; i < rawArgs.size(); i++) {
                rest.add(ReaderDatum.toValue(rawArgs.get(i)));
            }
            bindings.put(macro.restName(), Value.list(rest));
        }

        Object result = Value.NIL;
        for (Object body : macro.body()) {
            result = eval(body, bindings, registry);
        }
        return result;
    }

    private static Object eval(
            Object form,
            Map<String, Object> bindings,
            CanonRegistry registry) {
        if (form == Value.NIL) return Value.NIL;
        if (form instanceof Token token) {
            Object bound = bindings.get(token.spelling());
            return bound != null ? bound : Value.symbol(token.spelling());
        }
        if (form instanceof Value.Symbol
                || form instanceof Value.NumberValue
                || form instanceof Value.StringValue) {
            return form;
        }
        if (!(form instanceof Value.Pair pair)) {
            throw new WsmError(
                    WsmError.Kind.INVALID_FORM,
                    "macro body contains unsupported reader form");
        }

        List<Object> items = Compiler.items(pair);
        if (items.isEmpty()) return Value.NIL;

        Object head = items.get(0);
        String id = null;
        if (head instanceof Token token) {
            id = registry.semanticIdForToken(token.spelling());
        } else if (head instanceof Value.Symbol symbol) {
            id = registry.semanticIdForToken(symbol.name);
        }

        if (ID_QUOTE.equals(id)) {
            if (items.size() != 2) {
                throw new WsmError(WsmError.Kind.ARITY, ID_QUOTE + " expects 1 argument");
            }
            return ReaderDatum.toValue(items.get(1));
        }

        if (ID_CONS.equals(id) || ID_CAR.equals(id) || ID_CDR.equals(id)) {
            Object[] args = new Object[items.size() - 1];
            for (int i = 1; i < items.size(); i++) {
                args[i - 1] = eval(items.get(i), bindings, registry);
            }
            return SemanticMechanismTable.invoke(id, args);
        }

        throw new WsmError(
                WsmError.Kind.INVALID_FORM,
                "macro body semantic id is not an expansion mechanism: " + id);
    }
}
