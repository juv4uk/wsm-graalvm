#!/usr/bin/env bash
set -euo pipefail

REPO=$(cd "$(dirname "$0")/.." && pwd)
: "${G:=}"

bash "$REPO/scripts/fetch-third-party.sh"

CP="$REPO/classes:$REPO/third_party/truffle-api.jar:$REPO/third_party/polyglot.jar:$REPO/third_party/truffle-runtime.jar:$REPO/third_party/graalvm-collections.jar"

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/ContextSmoke.java" <<'JAVA'
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import java.nio.file.Path;

public final class ContextSmoke {
    public static void main(String[] args) {
        System.setProperty("wsm.registryPath", args[0]);
        try (Context context = Context.newBuilder("wsm").build()) {
            context.eval("wsm", "(def f (lambda () g))");
            context.eval("wsm", "(def g 42)");
            context.eval("wsm",
                    "(cond ((= (f) 42) (quote context-shared-ok)) "
                            + "(t (undefined-symbol)))");
        }

        try (Context isolated = Context.newBuilder("wsm").build()) {
            try {
                isolated.eval("wsm", "(f)");
                throw new AssertionError("independent context unexpectedly saw f");
            } catch (org.graalvm.polyglot.PolyglotException expected) {
                if (!expected.getMessage().contains("UnknownSymbol")
                        && !expected.getMessage().contains("unbound symbol")) {
                    throw expected;
                }
            }
        }

        System.out.println("CONTEXT-SHARED-FRAME-OK");
    }
}
JAVA

"$G/bin/javac" --release 25 -cp "$CP" -d "$TMP" "$TMP/ContextSmoke.java"
"$G/bin/java" -cp "$CP:$TMP" -Dtruffle.class.path.append="$REPO/classes" ContextSmoke "$1"
