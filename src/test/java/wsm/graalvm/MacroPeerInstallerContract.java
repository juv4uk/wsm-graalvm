package wsm.graalvm;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** Focused contract for Lisp-owned semantic 00000000001000000010 macro peer installation. */
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
                new LinkedHashSet<>(registry.row("00000000001000000010").surfaces().values());
        expected.remove("00000000001000000010");

        require(installed.equals(expected),
                "installed 00000000001000000010 peers differ from registry: " + installed + " vs " + expected);
        require(!globals.isMacro("00000000001000000010"),
                "opaque machine ID 00000000001000000010 must not become an ordinary macro binding");

        for (String spelling : expected) {
            require(globals.isMacro(spelling),
                    "missing registry-owned macro peer: " + spelling);
            require(globals.macro(spelling) == macro,
                    "all 00000000001000000010 peers must share the exact same MacroValue: " + spelling);

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
