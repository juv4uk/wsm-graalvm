package wsm.graalvm;

/** Focused mechanism witness for stable semantic ID 1043 string-append. */
public final class StringAppendContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object eval(Compiler compiler, String source) {
        Object last = Value.NIL;
        for (WsmNode node : compiler.compileProgram(new Reader(source).readAll())) {
            last = node.executeGeneric(null);
        }
        return last;
    }

    private static void expectError(
            Compiler compiler,
            String source,
            WsmError.Kind expected) {
        try {
            eval(compiler, source);
            throw new AssertionError("expected " + expected + ": " + source);
        } catch (WsmError error) {
            require(error.kind == expected,
                    "expected " + expected + ", got " + error.contractKind());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: StringAppendContract <semantic-registry.lisp>");
        }

        CanonRegistry registry = CanonRegistryLoader.load(args[0]);

        Object result = eval(
                new Compiler(registry),
                "(1043 \"hello \" \"світе\")");
        require(result instanceof Value.StringValue,
                "1043 must produce a language string value");
        require(((Value.StringValue) result).value.equals("hello світе"),
                "1043 must concatenate exact Unicode string contents");

        expectError(
                new Compiler(registry),
                "(1043 \"only-one\")",
                WsmError.Kind.ARITY);
        expectError(
                new Compiler(registry),
                "(1043 \"ok\" 42)",
                WsmError.Kind.TYPE);

        System.out.println("STRING-APPEND-1043-CONTRACT-OK");
    }
}
