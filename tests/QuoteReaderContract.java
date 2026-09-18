package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Executable reader/quote contract for GitHub #8. */
public final class QuoteReaderContract {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Object eval(CanonRegistry registry, String source) {
        Compiler compiler = new Compiler(registry);
        ProgramBodyNode body = new ProgramBodyNode(
                compiler.compileProgram(new Reader(source).readAll()));
        WsmLanguage.BodyRoot root = new WsmLanguage.BodyRoot(null, body);
        return root.getCallTarget().call();
    }

    private static void sameDatum(
            CanonRegistry registry,
            String a,
            String b,
            String label) {
        Object av = eval(registry, a);
        Object bv = eval(registry, b);
        require(WsmNode.Structural.equals(av, bv),
                label + ": " + Printer.print(av)
                        + " != " + Printer.print(bv));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: QuoteReaderContract <semantic-registry.lisp> <canon.lisp>");
        }

        CanonRegistry registry =
                new CanonRegistry().load(Files.readString(Path.of(args[0])));

        // Reader sugar and every admitted human/machine quote surface converge
        // to the same syntax identity 0001 behavior.
        sameDatum(registry, "'x", "(0001 x)", "apostrophe vs machine ID");
        sameDatum(registry, "'x", "(quote x)", "apostrophe vs EN");
        sameDatum(registry, "'x", "(як-є x)", "apostrophe vs UK");
        sameDatum(registry, "'x", "(svarūpa x)", "apostrophe vs SA");

        Object quoted = eval(registry, "'x");
        require(quoted instanceof Value.Symbol s && s.name.equals("x"),
                "quoted atom must be exact symbol x");

        Object list = eval(registry, "'(a b c)");
        require(Printer.print(list).equals("(a b c)"),
                "quoted proper list shape changed: " + Printer.print(list));

        Object dotted = eval(registry, "'(a b . c)");
        require(Printer.print(dotted).equals("(a b . c)"),
                "quoted dotted pair shape changed: " + Printer.print(dotted));

        Object uk = eval(registry, "'об'єкт");
        require(uk instanceof Value.Symbol s && s.name.equals("об'єкт"),
                "ASCII internal apostrophe split Ukrainian identifier");

        Object ukTypographic = eval(registry, "'об’єкт");
        require(ukTypographic instanceof Value.Symbol s && s.name.equals("об’єкт"),
                "typographic internal apostrophe split Ukrainian identifier");

        List<Object> raw = new Reader("об'єкт п'ять зв'язок").readAll();
        require(raw.size() == 3, "internal apostrophes created extra forms");
        require(((Reader.Token) raw.get(0)).spelling().equals("об'єкт"),
                "об'єкт token changed");
        require(((Reader.Token) raw.get(1)).spelling().equals("п'ять"),
                "п'ять token changed");
        require(((Reader.Token) raw.get(2)).spelling().equals("зв'язок"),
                "зв'язок token changed");

        // Regression: the old reader produced an improper (QUOTE_HEAD . datum)
        // and Compiler.items() rejected it before quote lowering.
        Object sugarForm = new Reader("'radio").readAll().get(0);
        List<Object> sugarItems = Compiler.items(sugarForm);
        require(sugarItems.size() == 2 && sugarItems.get(0) == Reader.QUOTE_HEAD,
                "apostrophe sugar must be a proper internal quote form");

        // The pinned Canon file itself must now be readable, including its
        // symbolic-surface witness containing 'атом/'ліве/'праве.
        String canonSource = Files.readString(Path.of(args[1]));
        List<Object> canonForms = new Reader(canonSource).readAll();
        require(!canonForms.isEmpty(), "real canon.lisp parsed no forms");

        System.out.println(
                "QUOTE-READER-CONTRACT-OK forms=" + canonForms.size());
    }
}
