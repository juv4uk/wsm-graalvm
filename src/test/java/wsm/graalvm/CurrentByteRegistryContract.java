package wsm.graalvm;

/**
 * #230 focused witness: the Graal registry boundary reads the current exact
 * byte-SID schema directly, without a legacy-to-new ID translation table.
 */
public final class CurrentByteRegistryContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String registrySource = """
                (
                  (binary 8)
                  (00000000 (en ()) (ук ()) (укр ()) (sa ()) (sym ()))
                  (00000001 (en quote) (ук як-є) (укр як-є) (sa svarūpa) (sym "'"))
                  (00000010 (en atom) (ук атом?) (укр атом?) (sa aṇu) (sym .?))
                  (00111010 (en string-append) (ук зчепити) (укр зчепити) (sa śabdasaṃyoga) (sym ()))
                )
                """;

        CanonRegistry registry = new CanonRegistry().load(registrySource);

        require(registry.usesExactByteSids(), "current registry must be exact byte-SID mode");
        require(registry.ids().size() == 4, "expected four registry rows");

        require(Sid8.bits(0,0,0,0,0,0,0,1).equals(registry.semanticIdForToken("quote")),
                "English quote surface must resolve to exact SID");
        require(Sid8.bits(0,0,0,0,0,0,0,1).equals(registry.semanticIdForToken("як-є")),
                "Ukrainian quote surface must resolve to exact SID");
        require(Sid8.bits(0,0,0,0,0,0,0,1).equals(registry.semanticIdForToken("'")),
                "quoted symbol spelling must decode as registry data");
        require(Sid8.bits(0,0,0,0,0,0,1,0).equals(registry.semanticIdForToken("атом?")),
                "Ukrainian atom surface must resolve to exact SID");
        require(Sid8.bits(0,0,0,0,0,0,1,0).equals(registry.semanticIdForToken(".?")),
                "symbol surface must resolve to exact SID");
        require(Sid8.bits(0,0,1,1,1,0,1,0).equals(registry.semanticIdForToken("string-append")),
                "string-append must preserve exact byte identity");
        require(Sid8.bits(0,0,0,0,0,0,1,0).equals(registry.semanticIdForToken("00000010")),
                "exact byte spelling itself must remain an admitted route");

        require(registry.semanticIdForToken("10") == null,
                "byte SIDs must not be reconstructed from decimal numeric values");

        String malformed = """
                (
                  (binary 8)
                  (0000010 (en atom) (ук атом?) (укр атом?) (sa aṇu) (sym .?))
                )
                """;
        String failure = "none";
        try {
            new CanonRegistry().load(malformed);
        } catch (WsmError e) {
            failure = e.kind.name();
        }
        require("PARSE".equals(failure),
                "non-eight-bit identity must fail closed, got " + failure);

        System.out.println("CURRENT-BYTE-REGISTRY-CONTRACT-OK rows=" + registry.ids().size());
    }
}
