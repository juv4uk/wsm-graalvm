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
                new LinkedHashSet<>(registry.row(Sid8.bits(0,0,0,0,1,0,1,0)).surfaces().values());

        require(installed.equals(expected),
                "installed 0012 peers differ from registry: " + installed + " vs " + expected);
        require(!globals.isMacro(MacroPeerInstaller.DEFMACRO_ID.toString()),
                "opaque machine ID 0012 must not become an ordinary String macro binding");
        require(globals.isMacro(MacroPeerInstaller.DEFMACRO_ID),
                "exact SID 00001010 must be installed as the macro identity key");
        require(globals.macro(MacroPeerInstaller.DEFMACRO_ID) == macro,
                "exact SID macro key must share the same Lisp-owned MacroValue");

        Compiler idCompiler = new Compiler(registry, null, globals);
        Object idForm = new Reader("(00001010 ignored)").readAll().get(0);
        Object idResult =
                idCompiler.compile(idForm, idCompiler.root()).executeGeneric(null);
        require(
                idResult instanceof Value.NumberValue idNumber
                        && idNumber.numerator().equals(BigInteger.valueOf(42))
                        && idNumber.denominator().equals(BigInteger.ONE),
                "exact SID macro head must expand through the installed MacroValue");

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
