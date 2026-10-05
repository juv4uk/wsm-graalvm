package wsm.graalvm;

/** Contract witness for the explicit one-way historical -> current projection. */
public final class LegacySid8ProjectionContract {
    private LegacySid8ProjectionContract() {}

    public static void main(String[] args) {
        require("D3:000", "00000000");
        require("D3:001", "00000001");
        require("D3:010", "00000010");
        require("D3:011", "00000110");
        require("D3:100", "00000101");
        require("D3:101", "00000011");
        require("D3:110", "00000111");
        require("D3:111", "00000100");

        require("D4:0010", "00001000");
        require("D4:0011", "00001001");
        require("D4:1111", "00101001");

        require(
                LegacySid8Projection.toCurrentDomainIdentity(Sid8.parseBareToken("10101000")) == null,
                "unmapped legacy invoke must not be guessed into a current domain"
        );
        require(
                LegacySid8Projection.toCurrentDomainIdentity(Sid8.parseBareToken("00000000"))
                        .equals(LegacySid8Projection.d3("000")),
                "legacy empty projection must remain exact D3:000"
        );

        System.out.println("LEGACY-SID8-PROJECTION-CONTRACT-GREEN");
    }

    private static void require(String expected, String legacyBits) {
        DomainIdentity actual = LegacySid8Projection.toCurrentDomainIdentity(
                Sid8.parseBareToken(legacyBits));
        if (actual == null || !expected.equals(actual.toString())) {
            throw new AssertionError(
                    legacyBits + " -> expected " + expected + ", got " + actual);
        }
    }
}
