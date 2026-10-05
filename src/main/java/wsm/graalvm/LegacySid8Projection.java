package wsm.graalvm;

/**
 * Explicit historical compatibility projection: old Function8/Sid8 rows to
 * the current ratified SENS exact-domain identities.
 *
 * This class is not semantic authority. The mapping is copied only from the
 * upstream SENS compatibility projection and is intentionally one-way.
 */
public final class LegacySid8Projection {
    private LegacySid8Projection() {}

    public static DomainIdentity toCurrentDomainIdentity(Sid8 legacy) {
        if (legacy == null) return null;

        return switch (legacy.toString()) {
            case "00000000" -> DomainIdentity.exact(3, "000");
            case "00000001" -> DomainIdentity.exact(3, "001");
            case "00000010" -> DomainIdentity.exact(3, "010");
            case "00000011" -> DomainIdentity.exact(3, "101");
            case "00000100" -> DomainIdentity.exact(3, "111");
            case "00000101" -> DomainIdentity.exact(3, "100");
            case "00000110" -> DomainIdentity.exact(3, "011");
            case "00000111" -> DomainIdentity.exact(3, "110");
            case "00001000" -> DomainIdentity.exact(4, "0010");
            case "00001001" -> DomainIdentity.exact(4, "0011");
            case "00101001" -> DomainIdentity.exact(4, "1111");
            case "00110011" -> DomainIdentity.exact(4, "1000");
            case "00110100" -> DomainIdentity.exact(4, "1001");
            case "00110101" -> DomainIdentity.exact(4, "0111");
            default -> null;
        };
    }

    public static DomainIdentity d3(String bits) {
        return DomainIdentity.exact(3, bits);
    }
}
