package wsm.graalvm;

import com.graalvm.polyglot.Context;

/** Focused contract for the upstream-required Closure -> Macro substrate. */
public final class BootstrapMacroSubstrateContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void expectKind(Runnable action, WsmError.Kind expected, String label) {
        try {
            action.run();
            throw new AssertionError(label + " did not fail");
        } catch (WsmError error) {
            require(
                    error.kind == expected,
                    label + " expected " + expected + ", got " + error.kind);
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: BootstrapMacroSubstrateContract <semantic-registry.lisp>");
        }

        System.setProperty("wsm.registryPath", args[0]);

        try (Context context = Context.newBuilder("wsm").build()) {
            Object result =
                    context.eval(
                            "wsm",
                            "(make-macro (lambda (x) x))");
            require(
                    result instanceof BootstrapMacroSubstrate.MacroValue,
                    "make-macro must materialize an opaque Macro value");
        }

        expectKind(
                () -> BootstrapMacroSubstrate.invoke(new Object[0]),
                WsmError.Kind.ARITY,
                "make-macro arity");

        expectKind(
                () -> BootstrapMacroSubstrate.invoke(new Object[] {Value.symbol("not-a-closure")}),
                WsmError.Kind.TYPE,
                "make-macro type");

        System.out.println("BOOTSTRAP-MACRO-SUBSTRATE-CONTRACT-OK");
    }
}
