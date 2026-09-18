package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** RED witness for #204: same pinned Lisp tail-recursion fixture on Graal. */
public final class DeepRecursionParityContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("usage: DeepRecursionParityContract <wsm-graalvm-root>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        Path corpusPath = repo.resolve("external/my-lisp/tests/fixtures/conformance.lisp");
        String corpus = Files.readString(corpusPath);
        List<ConformanceInventory.Fixture> tier2 = ConformanceInventory.selectTier(corpus, 2);
        ConformanceInventory.Fixture fixture = tier2.stream()
                .filter(f -> f.expr().contains("(count-down 100000)"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("pinned corpus lacks count-down 100000 witness"));
        require("done".equals(fixture.expected()),
                "pinned deep-recursion expected value drifted: " + fixture.expected());

        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);
        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        BootstrapRuntime.execute(context,
                Files.readString(repo.resolve("external/my-lisp/lib/canon.lisp")));
        BootstrapClosureLoader.Source macroSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/macro.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/macro.lisp"));
        Object macroValue = BootstrapRuntime.execute(context, macroSource.text());
        require(macroValue instanceof GlobalBindings.MacroValue,
                "macro bootstrap did not produce MacroValue: " + macroValue);
        MacroPeerInstaller.install(context.registry(), context.globals(),
                (GlobalBindings.MacroValue) macroValue);
        BootstrapClosureLoader.Source coreSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/core.lisp"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("manifest omitted lib/core.lisp"));
        BootstrapRuntime.execute(context, coreSource.text());

        String smallExpr = fixture.expr().replace("(count-down 100000)", "(count-down 100)");
        Object small = BootstrapRuntime.execute(context, smallExpr);
        require("done".equals(Printer.print(small)),
                "small recursion must prove binding/evaluation correctness first, got: "
                        + Printer.print(small));
        System.out.println("DEEP-RECURSION-SMALL-GREEN depth=100 result=done");

        try {
            Object deep = BootstrapRuntime.execute(context, fixture.expr());
            require("done".equals(Printer.print(deep)),
                    "deep pinned witness returned wrong value: " + Printer.print(deep));
            System.out.println("DEEP-TAIL-GREEN depth=100000 result=done");
        } catch (StackOverflowError error) {
            System.err.println("DEEP-TAIL-HOST-STACK-DEPENDENCE depth=100000 failure=StackOverflowError");
            throw error;
        } catch (Throwable error) {
            System.err.println("DEEP-TAIL-FAILURE depth=100000 type="
                    + error.getClass().getName() + " message=" + error.getMessage());
            throw error;
        }
    }
}
