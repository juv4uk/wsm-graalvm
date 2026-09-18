package wsm.graalvm;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RED contract for GitHub #2.
 *
 * This is intentionally outside src/ so the ordinary M0 build is not broken
 * while the ID-first refactor is in progress. Run with
 * scripts/test-semantic-identity.sh.
 */
public final class SemanticIdentityContract {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Object resolve(Compiler compiler, String spelling) {
        return compiler
                .compile(new Reader.Token(spelling), compiler.root())
                .executeGeneric(null);
    }

    public static void main(String[] args) {
        Map<String, CanonRegistry.Row> rows = new LinkedHashMap<>();
        rows.put("0005", new CanonRegistry.Row(
                "0005",
                Map.of("en", "car", "uk", "перше", "sa", "ādi")));

        Map<String, String> spellings = new LinkedHashMap<>();
        spellings.put("0005", "0005");
        spellings.put("car", "0005");
        spellings.put("перше", "0005");
        spellings.put("ādi", "0005");

        CanonRegistry registry = CanonRegistry.registry(rows, spellings);
        Compiler compiler = new Compiler(registry);

        Object byId = resolve(compiler, "0005");
        Object byEn = resolve(compiler, "car");
        Object byUk = resolve(compiler, "перше");
        Object bySa = resolve(compiler, "ādi");

        Value.SemanticRef expected = new Value.SemanticRef("0005");

        require(expected.equals(byId), "machine ID must materialize SemanticRef(0005)");
        require(expected.equals(byEn), "English surface must materialize SemanticRef(0005)");
        require(expected.equals(byUk), "Ukrainian surface must materialize SemanticRef(0005)");
        require(expected.equals(bySa), "Sanskrit surface must materialize SemanticRef(0005)");

        require(byId.equals(byEn) && byEn.equals(byUk) && byUk.equals(bySa),
                "all admitted surfaces must denote one semantic value");

        require(!(byEn instanceof WsmFunc),
                "canonical callable value must not be a Java WsmFunc object");

        System.out.println("SEMANTIC-IDENTITY-CONTRACT-OK");
    }
}
