package wsm.graalvm;

import java.util.Map;

/** Graal/JVM mechanisms keyed only by exact Sid8 function identity. */
public final class SemanticMechanismTable {

    @FunctionalInterface
    private interface Mechanism {
        Object invoke(Object[] args);
    }

    private static final Sid8 SID_00000010 = Sid8.bits(0,0,0,0,0,0,1,0);
    private static final Sid8 SID_00000011 = Sid8.bits(0,0,0,0,0,0,1,1);
    private static final Sid8 SID_00000100 = Sid8.bits(0,0,0,0,0,1,0,0);
    private static final Sid8 SID_00000101 = Sid8.bits(0,0,0,0,0,1,0,1);
    private static final Sid8 SID_00000110 = Sid8.bits(0,0,0,0,0,1,1,0);
    private static final Sid8 SID_00001101 = Sid8.bits(0,0,0,0,1,1,0,1);
    private static final Sid8 SID_00011010 = Sid8.bits(0,0,0,1,1,0,1,0);
    private static final Sid8 SID_00011011 = Sid8.bits(0,0,0,1,1,0,1,1);
    private static final Sid8 SID_00011100 = Sid8.bits(0,0,0,1,1,1,0,0);
    private static final Sid8 SID_00111010 = Sid8.bits(0,0,1,1,1,0,1,0);
    private static final Sid8 SID_01000011 = Sid8.bits(0,1,0,0,0,0,1,1);
    private static final Sid8 SID_01001100 = Sid8.bits(0,1,0,0,1,1,0,0);

    private static final Map<Sid8, Mechanism> TABLE = Map.ofEntries(
            Map.entry(SID_00000010, SemanticMechanismTable::invoke00000010),
            Map.entry(SID_00000011, SemanticMechanismTable::invoke00000011),
            Map.entry(SID_00000100, SemanticMechanismTable::invoke00000100),
            Map.entry(SID_00000101, SemanticMechanismTable::invoke00000101),
            Map.entry(SID_00000110, SemanticMechanismTable::invoke00000110),
            Map.entry(SID_00001101, SemanticMechanismTable::invoke00001101),
            Map.entry(SID_00011010, SemanticMechanismTable::invoke00011010),
            Map.entry(SID_00011011, SemanticMechanismTable::invoke00011011),
            Map.entry(SID_00011100, SemanticMechanismTable::invoke00011100),
            Map.entry(SID_01000011, SemanticMechanismTable::invoke01000011),
            Map.entry(SID_01001100, SemanticMechanismTable::invoke01001100),
            Map.entry(SID_00111010, SemanticMechanismTable::invoke00111010)
    );

    private SemanticMechanismTable() {}

    public static boolean supports(Sid8 sid) {
        return TABLE.containsKey(sid);
    }

    public static Object invoke(Sid8 sid, Object[] args) {
        Mechanism mechanism = TABLE.get(sid);
        if (mechanism == null) {
            throw new WsmError(
                    WsmError.Kind.TYPE,
                    "SID is not a callable substrate mechanism: " + sid);
        }
        return mechanism.invoke(args);
    }

    private static Object invoke00000010(Object[] args) {
        WsmError.arity(args, 1, SID_00000010.toString());
        return Value.structuralKind(args[0]);
    }

    private static Object invoke00000011(Object[] args) {
        WsmError.arity(args, 2, SID_00000011.toString());
        return WsmNode.EqNode.eqRecord(args[0], args[1]);
    }

    private static Object invoke00000100(Object[] args) {
        WsmError.arity(args, 2, SID_00000100.toString());
        return new Value.Pair(args[0], args[1]);
    }

    private static Object invoke00000101(Object[] args) {
        WsmError.arity(args, 1, SID_00000101.toString());
        if (!(args[0] instanceof Value.Pair pair)) {
            throw new WsmError(WsmError.Kind.TYPE, SID_00000101 + " expects a pair");
        }
        return pair.car;
    }

    private static Object invoke00000110(Object[] args) {
        WsmError.arity(args, 1, SID_00000110.toString());
        if (!(args[0] instanceof Value.Pair pair)) {
            throw new WsmError(WsmError.Kind.TYPE, SID_00000110 + " expects a pair");
        }
        return pair.cdr;
    }

    private static Object invoke00001101(Object[] args) {
        if (args.length == 0) {
            throw new WsmError(WsmError.Kind.ARITY, SID_00001101 + " expects at least 1 argument");
        }
        Value.NumberValue result = requireNumber(SID_00001101, args[0]);
        if (args.length == 1) {
            return new Value.NumberValue(result.numerator().negate(), result.denominator());
        }
        for (int i = 1; i < args.length; i++) {
            Value.NumberValue operand = requireNumber(SID_00001101, args[i]);
            result = subtractExact(result, operand);
        }
        return result;
    }

    private static Value.NumberValue subtractExact(
            Value.NumberValue left, Value.NumberValue right) {
        return new Value.NumberValue(
                left.numerator().multiply(right.denominator())
                        .subtract(right.numerator().multiply(left.denominator())),
                left.denominator().multiply(right.denominator()));
    }

    private static Object invoke00011010(Object[] args) {
        return exactQComparison(SID_00011010, args, -1);
    }

    private static Object invoke00011011(Object[] args) {
        return exactQComparison(SID_00011011, args, 1);
    }

    private static Object invoke00011100(Object[] args) {
        return exactQComparison(SID_00011100, args, 0);
    }

    private static Object exactQComparison(Sid8 sid, Object[] args, int relation) {
        if (args.length == 0) {
            throw new WsmError(
                    WsmError.Kind.ARITY,
                    sid + ": expected at least 1 argument, received 0");
        }
        Value.NumberValue previous = requireNumber(sid, args[0]);
        boolean holds = true;
        for (int i = 1; i < args.length; i++) {
            Value.NumberValue current = requireNumber(sid, args[i]);
            int comparison = compareExact(previous, current);
            if (Integer.signum(comparison) != relation) {
                holds = false;
            }
            previous = current;
        }
        return Value.NumberValue.integer(java.math.BigInteger.valueOf(holds ? 1L : 0L));
    }

    private static Value.NumberValue requireNumber(Sid8 sid, Object value) {
        if (!(value instanceof Value.NumberValue number)) {
            throw new WsmError(WsmError.Kind.TYPE, sid + " expects an exact-rational sequence");
        }
        return number;
    }

    private static int compareExact(Value.NumberValue left, Value.NumberValue right) {
        return left.numerator().multiply(right.denominator())
                .compareTo(right.numerator().multiply(left.denominator()));
    }

    private static Object invoke01000011(Object[] args) {
        WsmError.arity(args, 1, SID_01000011.toString());
        if (!(args[0] instanceof Value.StringValue text)) {
            throw new WsmError(WsmError.Kind.TYPE, SID_01000011 + " expects a string");
        }
        return Value.symbol(text.value);
    }

    private static Object invoke01001100(Object[] args) {
        WsmError.arity(args, 1, SID_01001100.toString());
        return new Value.StringValue(CanonicalSerializer.write(args[0]));
    }

    private static Object invoke00111010(Object[] args) {
        if (args.length != 2) {
            throw new WsmError(WsmError.Kind.ARITY, SID_00111010 + " expects 2 string arguments");
        }
        StringBuilder out = new StringBuilder();
        for (Object a : args) {
            if (!(a instanceof Value.StringValue text)) {
                throw new WsmError(WsmError.Kind.TYPE, SID_00111010 + " expects strings");
            }
            out.append(text.value);
        }
        return new Value.StringValue(out.toString());
    }
}
