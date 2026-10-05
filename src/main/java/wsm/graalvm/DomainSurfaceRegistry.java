package wsm.graalvm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mechanical loader for the Lisp-owned exact-domain human-surface projection.
 *
 * It preserves domain identity and surface role. Status columns are routing
 * metadata owned by the upstream projection contract and are deliberately not
 * interpreted as semantic meaning here.
 */
public final class DomainSurfaceRegistry {

    public record Surface(
            DomainIdentity identity,
            String role,
            String namespace,
            String spelling,
            String status) {}

    private final Map<String, Surface> byKey = new LinkedHashMap<>();

    public static DomainSurfaceRegistry load(String... registrySources) {
        Objects.requireNonNull(registrySources, "registrySources");
        DomainSurfaceRegistry out = new DomainSurfaceRegistry();
        for (String source : registrySources) {
            Objects.requireNonNull(source, "registry source");
            out.loadOne(source);
        }
        return out;
    }

    private void loadOne(String source) {
        var forms = new RegistrySexpReader(source).readAll();
        for (Object form : forms) {
            if (!(form instanceof java.util.List<?> top) || top.isEmpty()) {
                throw parse("surface registry top-level form must be a non-empty list");
            }

            Object name = top.get(0);
            if ("domain-surfaces-d1-d4".equals(name)
                    || "domain-surfaces-d5".equals(name)) {
                loadRows(top);
                continue;
            }
            throw parse("unknown surface registry form: " + name);
        }
    }

    private void loadRows(java.util.List<?> top) {
        for (int i = 1; i < top.size(); i++) {
            Object item = top.get(i);
            if (!(item instanceof java.util.List<?> row) || row.isEmpty()) {
                continue;
            }
            if (!"row".equals(row.get(0))) {
                continue;
            }
            parseRow(row);
        }
    }

    private void parseRow(java.util.List<?> row) {
        if (row.size() != 9
                || !(row.get(1) instanceof String domainToken)
                || !(row.get(2) instanceof String bits)
                || !(row.get(3) instanceof String role)
                || !(row.get(4) instanceof String en)
                || !(row.get(5) instanceof String uk)
                || !(row.get(6) instanceof String sa)
                || !(row.get(7) instanceof String ukStatus)
                || !(row.get(8) instanceof String saStatus)) {
            throw parse("malformed exact-domain surface row");
        }

        if (!domainToken.matches("D[1-5]")) {
            throw parse("surface row domain must be D1..D5: " + domainToken);
        }
        int domain = Integer.parseInt(domainToken.substring(1));
        DomainIdentity identity = DomainIdentity.exact(domain, bits);

        putSurface(namespaceKey("en", en), identity, role, "en", en, ukStatus);
        putSurface(namespaceKey("ук", uk), identity, role, "ук", uk, ukStatus);
        putSurface(namespaceKey("sa", sa), identity, role, "sa", sa, saStatus);
    }

    private static String namespaceKey(String namespace, String spelling) {
        return namespace + "\u0000" + spelling;
    }

    private void putSurface(
            String key,
            DomainIdentity identity,
            String role,
            String namespace,
            String spelling,
            String status) {
        if (spelling == null || spelling.isEmpty()) {
            return;
        }
        Surface previous = byKey.putIfAbsent(
                key,
                new Surface(identity, role, namespace, spelling, status));
        if (previous != null && !previous.identity().equals(identity)) {
            throw parse(
                    "surface collision: " + namespace + ":" + spelling
                            + " maps to both " + previous.identity()
                            + " and " + identity);
        }
    }

    public Surface resolve(String namespace, String spelling) {
        return byKey.get(namespaceKey(namespace, spelling));
    }

    public Map<String, Surface> entries() {
        return Collections.unmodifiableMap(byKey);
    }

    private static WsmError parse(String message) {
        return new WsmError(WsmError.Kind.PARSE, message);
    }
}
