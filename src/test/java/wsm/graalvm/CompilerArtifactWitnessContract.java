package wsm.graalvm;

import java.math.BigInteger;

/**
 * #290 bounded backend witness for a SENS-carried compiler role.
 *
 * This class never reads D-domain coordinates and never converts the current
 * identity into a legacy function ID.  The upstream artifact has already
 * established the semantic role; this witness owns only a Graal/JVM mechanism
 * for the admitted selector-head case.
 */
public final class CompilerArtifactWitnessContract {
    private static final String ADMITTED_ROLE = "selector-head";
    private static final String MECHANISM = "graal.jvm.selector-head-v1";

    private static Value.NumberValue integer(String text) {
        return Value.NumberValue.integer(new BigInteger(text));
    }

    private static Object invokeSelectorHead(Object input) {
        if (!(input instanceof Value.Pair pair)) {
            throw new IllegalArgumentException("TYPE: selector-head mechanism requires pair input");
        }
        return pair.car;
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            throw new IllegalArgumentException("usage: <carried-role> <left> <right>");
        }
        String carriedRole = args[0];
        if (!ADMITTED_ROLE.equals(carriedRole)) {
            throw new IllegalArgumentException(
                    "BLOCKED-MECHANISM: unsupported carried compiler role " + carriedRole);
        }

        Object input = new Value.Pair(integer(args[1]), integer(args[2]));
        String observable = CanonicalSerializer.write(invokeSelectorHead(input));
        System.out.println("GRAAL-COMPILER-ARTIFACT-MECHANISM=" + MECHANISM);
        System.out.println("GRAAL-COMPILER-ARTIFACT-OBSERVABLE=" + observable);
    }
}
