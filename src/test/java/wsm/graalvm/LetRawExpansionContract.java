package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Raw-expansion diagnostic for #130 using the real pinned Lisp let macro. */
public final class LetRawExpansionContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String spelling(CanonRegistry registry, String id) {
        return registry.row(id).surfaces().values().stream()
                .filter(s -> s != null && !s.isBlank() && !id.equals(s))
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElseThrow(() -> new AssertionError("no admitted surface for " + id));
    }

    private static Object oneForm(String source) {
        var forms = new Reader(source).readAll();
        require(forms.size() == 1, "expected one reader form for: " + source);
        return forms.get(0);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: LetRawExpansionContract <wsm-graalvm-root>");
        }

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        BootstrapClosureLoader.Closure closure = BootstrapClosureLoader.load(repo);

        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        BootstrapRuntime.executeAuthoritySource(
                context,
                Files.readString(repo.resolve("external/my-lisp/lib/canon.lisp")));

        BootstrapClosureLoader.Source macroSource = closure.executableSources().stream()
                .filter(s -> s.path().endsWith("lib/macro.lisp"))
                .findFirst()
                .orElseThrow();
        Object macroValue = BootstrapRuntime.execute(context, macroSource.text());
        require(macroValue instanceof GlobalBindings.MacroValue,
                "macro.lisp did not return MacroValue: " + macroValue);
        MacroPeerInstaller.install(
                context.registry(),
                context.globals(),
                (GlobalBindings.MacroValue) macroValue);

        BootstrapClosureLoader.Source coreSource = BootstrapClosureLoader.load(repo).executableSources().stream()
                .filter(s -> s.path().endsWith("lib/core.lisp"))
                .findFirst()
                .orElseThrow();
        BootstrapRuntime.executeAuthoritySource(context, coreSource.text());

        String let = spelling(context.registry(), "1141");
        GlobalBindings.MacroValue letMacro = context.globals().macro(let);

        Object bindings = ReaderDatum.toValue(oneForm("((cutover-value 42))"));
        Object body = ReaderDatum.toValue(oneForm("cutover-value"));

        Object expanded = letMacro.expand(new Object[] {bindings, body});
        String printed = Printer.print(expanded);
        System.out.println("LET-RAW-EXPANSION=" + printed);

        require(
                "((lambda (cutover-value) cutover-value) 42)".equals(printed),
                "unexpected pinned let expansion: " + printed);

        Compiler compiler = new Compiler(context.registry(), null, context.globals());
        WsmNode compiled = compiler.compile(expanded, compiler.root());
        Object result = compiled.executeGeneric(null);
        require(
                "42".equals(Printer.print(result)),
                "raw pinned let expansion must execute to 42, got: " + Printer.print(result));

        Object sourceResult = BootstrapRuntime.execute(
                context,
                "(" + let + " ((cutover-value 42)) cutover-value)");
        require(
                "42".equals(Printer.print(sourceResult)),
                "source-level Lisp-owned let must execute to 42, got: "
                        + Printer.print(sourceResult));

        System.out.println("LET-RAW-EXPANSION-CONTRACT-OK");
    }
}
