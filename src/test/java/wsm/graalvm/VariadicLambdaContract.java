package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** RED/green contract for Tier-1 variadic lambda fixtures F25-F27. */
public final class VariadicLambdaContract {
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

    private static ConformanceInventory.Fixture fixture(
            List<ConformanceInventory.Fixture> fixtures,
            String id) {
        return fixtures.stream()
                .filter(f -> f.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing pinned fixture " + id));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: VariadicLambdaContract <conformance.lisp> <semantic-registry.lisp>");
        }

        List<ConformanceInventory.Fixture> tier1 =
                ConformanceInventory.selectTier(
                        Files.readString(Path.of(args[0])), 1);
        CanonRegistry registry = CanonRegistryLoader.load(args[1]);

        ConformanceInventory.Fixture f25 = fixture(tier1, "F25");
        ConformanceInventory.Fixture f26 = fixture(tier1, "F26");
        ConformanceInventory.Fixture f27 = fixture(tier1, "F27");

        Compiler compiler = new Compiler(registry);

        Object dotted = eval(compiler, f25.expr());
        require(Printer.print(dotted).equals(f25.expected()),
                "F25 dotted variadic mismatch: " + Printer.print(dotted));

        Object bare = eval(compiler, f26.expr());
        require(Printer.print(bare).equals(f26.expected()),
                "F26 bare-symbol variadic mismatch: " + Printer.print(bare));

        try {
            eval(compiler, f27.expr());
            throw new AssertionError("F27 must fail with Arity");
        } catch (WsmError error) {
            require(error.contractKind().equals(f27.error()),
                    "F27 expected " + f27.error()
                            + ", got " + error.contractKind());
        }

        System.out.println("VARIADIC-LAMBDA-CONTRACT-OK F25/F26/F27");
    }
}
