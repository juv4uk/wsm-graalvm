#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${G:=}"

if [ -z "${MYLISP:-}" ]; then
  echo "MYLISP must point at the pinned my-lisp checkout" >&2
  exit 2
fi

bash "$REPO/scripts/fetch-third-party.sh"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/Tier1Harness.java" <<'JAVA'
package wsm.graalvm;

import org.graalvm.polyglot.Context;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Tier1Harness {
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\\((expr|expected|error)\\s+\\.\\s+\"((?:\\\\.|[^\"])*)\"\\)");
    private static final Pattern TIER = Pattern.compile("\\(tier\\s+\\.\\s+1\\)");

    record Fixture(int line, String expr, String expected, String error) {}

    public static void main(String[] args) throws Exception {
        Path fixturePath = Path.of(args[0]);
        String registryPath = args[1];
        Path corePath = Path.of(args[2]);

        List<Fixture> fixtures = parseFixtures(Files.readAllLines(fixturePath));
        int passed = 0;
        int failed = 0;

        for (Fixture fixture : fixtures) {
            try {
                runFixture(fixture, registryPath, corePath);
                passed++;
                System.out.println("PASS line=" + fixture.line);
            } catch (Throwable failure) {
                failed++;
                System.err.println("FAIL line=" + fixture.line
                        + " expr=" + fixture.expr);
                System.err.println("  " + failure.getMessage());
            }
        }

        System.out.println("TIER1-SUMMARY passed=" + passed
                + " failed=" + failed
                + " total=" + fixtures.size());
        if (failed != 0) {
            throw new AssertionError("Tier-1 conformance diverged on "
                    + failed + " fixture(s)");
        }
        System.out.println("TIER1-GREEN " + passed + "/" + fixtures.size());
    }

    private static List<Fixture> parseFixtures(List<String> lines) {
        List<Fixture> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("((expr .") || !TIER.matcher(line).find()) {
                continue;
            }

            Matcher matcher = STRING_FIELD.matcher(line);
            String expr = null;
            String expected = null;
            String error = null;
            while (matcher.find()) {
                String value = unescape(matcher.group(2));
                switch (matcher.group(1)) {
                    case "expr" -> expr = value;
                    case "expected" -> expected = value;
                    case "error" -> error = value;
                    default -> throw new AssertionError();
                }
            }

            if (expr == null || (expected == null) == (error == null)) {
                throw new IllegalArgumentException(
                        "malformed conformance fixture at line " + (i + 1));
            }
            out.add(new Fixture(i + 1, expr, expected, error));
        }
        return out;
    }

    private static void runFixture(
            Fixture fixture,
            String registryPath,
            Path corePath) throws Exception {
        System.setProperty("wsm.registryPath", registryPath);
        try (Context context = Context.newBuilder("wsm").build()) {
            try {
                context.eval("wsm", Files.readString(corePath));
            } catch (org.graalvm.polyglot.PolyglotException coreFailure) {
                throw new AssertionError(
                        "core bootstrap failed: " + coreFailure.getMessage(),
                        coreFailure);
            }

            if (fixture.error() != null) {
                try {
                    context.eval("wsm", fixture.expr());
                } catch (org.graalvm.polyglot.PolyglotException expectedFailure) {
                    Throwable host = expectedFailure.isHostException()
                            ? expectedFailure.asHostException()
                            : expectedFailure;
                    String observed = host instanceof WsmError error
                            ? error.contractKind()
                            : expectedFailure.getMessage();
                    if (fixture.error().equals(observed)
                            || (observed != null && observed.contains(fixture.error()))) {
                        return;
                    }
                    throw new AssertionError(
                            "expected error " + fixture.error()
                                    + " but observed " + observed);
                }
                throw new AssertionError(
                        "expected error " + fixture.error() + " but expression succeeded");
            }

            String probe = "(cond ((equal? " + fixture.expr() + " (quote "
                    + fixture.expected()
                    + ")) (quote tier1-ok)) "
                    + "(t (undefined-symbol)))";
            try {
                context.eval("wsm", probe);
            } catch (org.graalvm.polyglot.PolyglotException mismatch) {
                throw new AssertionError(
                        "expected " + fixture.expected()
                                + " but equality probe failed: "
                                + mismatch.getMessage());
            }
        }
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (!escaped) {
                if (c == '\\') {
                    escaped = true;
                } else {
                    out.append(c);
                }
                continue;
            }
            switch (c) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                default -> throw new IllegalArgumentException(
                        "unsupported fixture escape \\" + c);
            }
            escaped = false;
        }
        if (escaped) {
            throw new IllegalArgumentException("unterminated fixture escape");
        }
        return out.toString();
    }
}
JAVA

"$G/bin/javac" --release 25 -cp "$CP" -d "$TMP" "$TMP/Tier1Harness.java"
"$G/bin/java" -cp "$CP:$TMP" -Dtruffle.class.path.append="$REPO/classes"   wsm.graalvm.Tier1Harness   "$MYLISP/tests/fixtures/conformance.lisp"   "$MYLISP/lib/surface/semantic-registry.lisp"   "$MYLISP/lib/core.lisp"
