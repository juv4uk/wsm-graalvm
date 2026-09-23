package wsm.graalvm;

import java.util.Objects;

/**
 * Exact eight-bit function identity.
 *
 * The language identity is the ordered eight-bit sequence itself. This class
 * deliberately exposes no decimal/hex constructor and no public String parser.
 * Source readers may normalize one already-bare eight-bit token through the
 * package-private parseBareToken boundary; all downstream code carries Sid8.
 */
public final class Sid8 implements Comparable<Sid8> {
    private final byte packed;

    private Sid8(byte packed) {
        this.packed = packed;
    }

    /** Construct an identity from exactly eight binary bits, most-significant first. */
    public static Sid8 bits(
            int b7, int b6, int b5, int b4, int b3, int b2, int b1, int b0) {
        int[] bits = {b7, b6, b5, b4, b3, b2, b1, b0};
        int value = 0;
        for (int bit : bits) {
            if (bit != 0 && bit != 1) {
                throw new IllegalArgumentException("Sid8 accepts only eight binary bits");
            }
            value = (value << 1) | bit;
        }
        return new Sid8((byte) value);
    }

    /** Reader-only normalization of one bare source token. */
    static Sid8 parseBareToken(String token) {
        Objects.requireNonNull(token, "token");
        if (token.length() != 8) {
            throw new IllegalArgumentException("Sid8 token must contain exactly eight bits");
        }
        int value = 0;
        for (int i = 0; i < 8; i++) {
            char c = token.charAt(i);
            if (c != '0' && c != '1') {
                throw new IllegalArgumentException("Sid8 token must contain only 0 or 1");
            }
            value = (value << 1) | (c - '0');
        }
        return new Sid8((byte) value);
    }

    boolean matchesBareToken(String token) {
        return toString().equals(token);
    }

    @Override
    public String toString() {
        int value = Byte.toUnsignedInt(packed);
        char[] out = new char[8];
        for (int i = 7; i >= 0; i--) {
            out[7 - i] = ((value >>> i) & 1) == 0 ? '0' : '1';
        }
        return new String(out);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Sid8 sid && packed == sid.packed;
    }

    @Override
    public int hashCode() {
        return Byte.hashCode(packed);
    }

    @Override
    public int compareTo(Sid8 other) {
        return Integer.compare(Byte.toUnsignedInt(packed), Byte.toUnsignedInt(other.packed));
    }
}
