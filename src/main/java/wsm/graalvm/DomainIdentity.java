package wsm.graalvm;

import java.util.Objects;

/**
 * Exact SENS semantic identity: the domain width and its payload bits are one
 * identity.  No decimal/hex coercion and no implicit Sid8 conversion exist.
 *
 * This carrier is intentionally domain-agnostic: admission of a domain for
 * execution (for example D1-D7 current versus D8 research) belongs to the
 * upstream contract/mechanism boundary, not to this value object.
 */
public record DomainIdentity(int domain, String bits) implements Comparable<DomainIdentity> {
    public DomainIdentity {
        if (domain <= 0) {
            throw new IllegalArgumentException("domain must be positive");
        }
        Objects.requireNonNull(bits, "bits");
        if (bits.length() != domain) {
            throw new IllegalArgumentException(
                    "exact-domain payload width mismatch: D" + domain
                            + " requires " + domain + " bits, got " + bits.length());
        }
        for (int i = 0; i < bits.length(); i++) {
            char bit = bits.charAt(i);
            if (bit != '0' && bit != '1') {
                throw new IllegalArgumentException(
                        "domain payload must contain only binary bits: " + bits);
            }
        }
    }

    public static DomainIdentity exact(int domain, String bits) {
        return new DomainIdentity(domain, bits);
    }

    @Override
    public int compareTo(DomainIdentity other) {
        int domainOrder = Integer.compare(domain, other.domain);
        return domainOrder != 0 ? domainOrder : bits.compareTo(other.bits);
    }

    @Override
    public String toString() {
        return "D" + domain + ":" + bits;
    }
}
