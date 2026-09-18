package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Diagnostic witness for #204.
 *
 * The upstream my-lisp reference test uses a 5,000-step tail-call chain and
 * expects constant Rust stack usage. This Graal witness deliberately records
 * the current substrate behavior before any TCO implementation is chosen.
 *
 * It is record-only by design: StackOverflowError is evidence, not a passing
 * semantic result. Once a substrate TCO mechanism is ratified, this witness
 * becomes a blocking GREEN contract for the tail-recursive case while the
 * non-tail control must remain stack-growing.
 */
public final class TailCallContract {
    private static final int DEPTH = 5_000;

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static String chainSource(boolean nonTail) {
        StringBuilder source = new StringBuilder(DEPTH * 48);
        for (int i = DEPTH - 1; i >= 0; i--) {
            source.append("(def step-").append(i)
                    .append(" (lambda () ");
            if (i == DEPTH - 1) {
                source.append("(quote done)");
            } else if (nonTail) {
                source.append("(cons (step-").append(i + 1)
                        .append(") (quote ()))");
            } else {
                source.append("(step-").append(i + 1).append(")");
            }
            source.append(") ) ");
        }
        source.append("(step-0)");
        return source.toString();
    }

    private static String run(WsmContext context, String source) {
        try {
            Object result = BootstrapRuntime.execute(context, source);
            return "value:" + Printer.print(result);
        } catch (StackOverflowError overflow) {
            return "stack-overflow";
        } catch (WsmError error) {
            return "lisp-error:" + error.contractKind();
        } catch (RuntimeException host) {
            return "host:" + host.getClass().getSimpleName();
        }
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 1,
                "usage: TailCallContract <wsm-graalvm-root>");

        Path repo = Path.of(args[0]).toAbsolutePath().normalize();
        Path manifest = repo.resolve("refs/lisp-dependency-manifest.lisp");
        Path reference = repo.resolve(
                "external/my-lisp/crates/my-lisp/tests/mccarthy.rs");

        require(Files.exists(manifest), "dependency manifest must exist");
        require(Files.exists(reference), "pinned my-lisp reference witness must exist");

        String referenceSource = Files.readString(reference);
        require(referenceSource.contains(
                        "fn tail_recursion_uses_constant_rust_stack()"),
                "pinned reference must contain the tail recursion witness");
        require(referenceSource.contains("let depth = 5_000;"),
                "pinned reference must use the agreed depth 5_000");

        BootstrapClosureLoader.Closure closure =
                BootstrapClosureLoader.load(repo);

        WsmContext context = new WsmContext(closure.registryPath().toString());
        context.initialize();

        // Canon is enough for this witness: def/lambda/quote are syntax/control
        // mechanisms already admitted by the current Compiler. No TCO-specific
        // Java semantics are introduced by the witness.
        BootstrapRuntime.executeAuthoritySource(
                context,
                Files.readString(repo.resolve("external/my-lisp/lib/canon.lisp")));

        String tail = run(context, chainSource(false));
        String nonTail = run(context, chainSource(true));

        String wsmHead = "unknown";
        String myLispPin = "unknown";
        try {
            Process wsm = new ProcessBuilder(
                    "git", "-C", repo.toString(), "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            wsmHead = new String(wsm.getInputStream().readAllBytes()).trim();

            Process lisp = new ProcessBuilder(
                    "git", "-C", repo.resolve("external/my-lisp").toString(),
                    "rev-parse", "HEAD")
                    .redirectErrorStream(true)
                    .start();
            myLispPin = new String(lisp.getInputStream().readAllBytes()).trim();
        } catch (Exception ignored) {
            // The source itself remains the authority; CI normally provides git.
        }

        System.out.println(
                "TCO-WITNESS"
                        + " version=1"
                        + " wsm_head=" + wsmHead
                        + " my_lisp_pin=" + myLispPin
                        + " depth=" + DEPTH
                        + " reference=crates/my-lisp/tests/mccarthy.rs"
                        + " tail=" + tail
                        + " non_tail=" + nonTail);
    }
}
