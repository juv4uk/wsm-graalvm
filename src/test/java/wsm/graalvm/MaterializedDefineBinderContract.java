package wsm.graalvm;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Focused data→code contract for DEFINE binders emitted as Lisp Symbol values. */
public final class MaterializedDefineBinderContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Value.NumberValue number(long value) {
        return Value.NumberValue.integer(BigInteger.valueOf(value));
    }

    private static void requireNumber(Object value, long expected, String message) {
        require(value instanceof Value.NumberValue, message + ": " + value);
        Value.NumberValue n = (Value.NumberValue) value;
        require(
                n.numerator().equals(BigInteger.valueOf(expected))
                        && n.denominator().equals(BigInteger.ONE),
                message + ": " + n);
    }

    private static Object execute(Compiler compiler, Object form) {
        return compiler.compile(form, compiler.root()).executeGeneric(null);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: MaterializedDefineBinderContract <semantic-registry.lisp>");
        }

        CanonRegistry registry =
                CanonRegistryLoader.fromText(Files.readString(Path.of(args[0])));
        Compiler compiler = new Compiler(registry);

        Object materializedDefine =
                Value.list(
                        List.of(
                                Value.symbol("0011"),
                                Value.symbol("generated-binding"),
                                number(42)));
        requireNumber(
                execute(compiler, materializedDefine),
                42,
                "materialized DEFINE must evaluate its value");
        requireNumber(
                execute(compiler, Value.symbol("generated-binding")),
                42,
                "materialized DEFINE binding must be readable normally");

        Object sourceDefine = new Reader("(0011 source-binding 7)").readAll().get(0);
        requireNumber(
                execute(compiler, sourceDefine),
                7,
                "reader Token DEFINE binder must remain supported");
        requireNumber(
                execute(compiler, Value.symbol("source-binding")),
                7,
                "source DEFINE binding must remain readable");

        Object canonBinder =
                Value.list(
                        List.of(
                                Value.symbol("0011"),
                                Value.symbol("0005"),
                                number(1)));
        try {
            execute(compiler, canonBinder);
            throw new AssertionError("materialized Canon binder must be rejected");
        } catch (WsmError error) {
            require(
                    error.kind == WsmError.Kind.INVALID_FORM,
                    "Canon binder must remain InvalidForm, got: " + error.kind);
        }

        Object numericBinder =
                Value.list(
                        List.of(
                                Value.symbol("0011"),
                                number(99),
                                number(1)));
        try {
            execute(compiler, numericBinder);
            throw new AssertionError("non-symbol DEFINE binder must be rejected");
        } catch (WsmError error) {
            require(
                    error.kind == WsmError.Kind.INVALID_FORM,
                    "non-symbol binder must be InvalidForm, got: " + error.kind);
        }

        System.out.println("MATERIALIZED-DEFINE-BINDER-CONTRACT-OK");
    }
}
