package wsm.graalvm;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Focused contract for Lisp-owned semantic 0012 macro peer installation. */
public final class MacroPeerInstallerContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: MacroPeerInstallerContract <semantic-registry.lisp>");
        }

        CanonRegistry registry =
                CanonRegistryLoader.fromText(Files.readString(Path.of(args[0])));
        GlobalBindings globals = new GlobalBindings();

        GlobalBindings.MacroValue macro =
                new GlobalBindings.MacroValue(
                        syntaxArgs -> Value.NumberValue.integer(BigInteger.valueOf(42)));

        Set<String> installed = MacroPeerInstaller.install(registry, globals, macro);
        Set<String> expected =
                new LinkedHashSet<>(registry.row("0012").surfaces().values());
        expected.remove("0012");

        require(installed.equals(expected),
                "installed 0012 peers differ from registry: " + installed + " vs " + expected);
        require(!globals.isMacro("0012"),
                "opaque machine ID 0012 must not become an ordinary macro binding");

        for (String spelling : expected) {
            require(globals.isMacro(spelling),
                    "missing registry-owned macro peer: " + spelling);
            require(globals.macro(spelling) == macro,
                    "all 0012 peers must share the exact same MacroValue: " + spelling);

            Compiler compiler = new Compiler(registry, null, globals);
            Object form = new Reader("(" + spelling + " ignored)").readAll().get(0);
            WsmNode compiled = compiler.compile(form, compiler.root());
            Object result = compiled.executeGeneric(null);
            require(
                    result instanceof Value.NumberValue n
                            && n.numerator().equals(BigInteger.valueOf(42))
                            && n.denominator().equals(BigInteger.ONE),
                    "peer did not expand through installed Lisp MacroValue: " + spelling);
        }

        System.out.println(
                "MACRO-PEER-INSTALLER-OK peers=" + installed.size());
    }
}
