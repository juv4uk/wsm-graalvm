package wsm.graalvm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Runtime top-level bindings plus compile-time declaration knowledge.
 *
 * Lambda locals do not live here; they live in Truffle frames. The declaration
 * set exists only so a sequential top-level define can shadow an ordinary
 * non-Canon registry surface while recursive definitions resolve themselves.
 */
final class GlobalBindings {
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, MacroDefinition> macros = new HashMap<>();
    private final Set<String> declared = new HashSet<>();

    GlobalBindings() {
        declare("t");
        define("t", Value.symbol("t"));
    }

    void declare(String name) {
        declared.add(name);
    }

    boolean isDeclared(String name) {
        return declared.contains(name);
    }

    void define(String name, Object value) {
        declared.add(name);
        values.put(name, value);
    }

    Object lookup(String name) {
        if (!values.containsKey(name)) {
            throw new WsmError(
                    WsmError.Kind.UNKNOWN_SYMBOL,
                    "unbound symbol: " + name);
        }
        return values.get(name);
    }

    void defineMacro(String name, MacroDefinition macro) {
        declared.add(name);
        macros.put(name, macro);
    }

    boolean isMacro(String name) {
        return macros.containsKey(name);
    }

    MacroDefinition macro(String name) {
        MacroDefinition macro = macros.get(name);
        if (macro == null) {
            throw new WsmError(
                    WsmError.Kind.UNKNOWN_SYMBOL,
                    "unknown macro: " + name);
        }
        return macro;
    }
