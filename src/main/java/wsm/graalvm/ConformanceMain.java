package wsm.graalvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gate 6 (issue #9): tier-1 conformance fixtures through the substrate's
 * own reader/eval — no silent pass. Each fixture prints its expected and
 * actual as Lisp data; a fixture passes only when they are the SAME
 * structural value, and the run exits non-zero on any divergence.
 */
public final class ConformanceMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ConformanceMain <fixtures.lisp> <registry.lisp>");
            System.exit(2);
        }
        String fixturesSource = Files.readString(Path.of(args[0]));
        String registryPath = args[1];
        CanonRegistry registry = CanonRegistryLoader.load(registryPath);

        Reader dataReader = new Reader(fixturesSource, true);
        List<Object> records = dataReader.readAll();

int total = 0, pass = 0, skip = 0; int totalSeen = 0;
        List<String> failures = new ArrayList<>();
        for (Object rec : records) {
            if (!(rec instanceof Value.Pair headPair)) continue;
            // one flat alist per fixture: (expr . "...") (expected . "...") (tier . 1) ...
            String expr = null, expected = null, error = null;
            long tier = 2;
            Value.Pair cur = headPair;
            
            while (cur != Value.NIL) {
                if (cur.car instanceof Value.Pair field) {
                    Object key = field.car;
                    Object val = null;
                    // (k . v) dotted pairs: the value may sit directly at cdr
                    if (field.cdr instanceof Value.Pair tail1) val = tail1.car;
                    if (field.cdr instanceof Value.Str st) val = st;
                    if (field.cdr instanceof Reader.Token t) val = t;
                    // data-mode reader keeps every plain atom as a Token (lexeme datum)
                    String k;
                    if (key instanceof Value.Symbol sk) k = sk.name;
                    else if (key instanceof Reader.Token tk) k = tk.spelling();
                    else continue;
                    {
                    Object vv = val == null ? null : rawString(val);
                        if (k.equals("expr") && vv != null) expr = (String) vv;
                        else if (k.equals("expected") && vv != null) expected = (String) vv;
                        else if (k.equals("error") && vv != null) error = (String) vv;
                                                else if (k.equals("tier")) tier =
                                val instanceof Long l ? l
                              : val instanceof Reader.Token t && t.spelling().matches("\\d+") ? Long.parseLong(t.spelling())
                              : tier;
                    }
                }
                cur = cur.cdr instanceof Value.Pair next ? next : null;
                if (cur == null) break;
            }
            if (expr == null || tier != 1) continue;   // M1 = tier 1 only
            total++; totalSeen++;
            if (error != null) { skip++; report("SKIP", expr, expected); continue; }

            Compiler compiler = new Compiler(registry);
            String result;
            try {
                List<Object> program = new Reader(expr).readAll();
                Object last = null;
                for (Object form : program) {
                    WsmNode node = compiler.compile(form, compiler.root());
                    last = node.executeGeneric(null);
                }
                result = last == null ? null : Printer.print(last);
            } catch (WsmError we) {
                result = "throw:" + we.kind;
            }
            if (expected != null && expected.equals(result)) {
                pass++;
                report("PASS", expr, expected);
            } else {
                report("FAIL", expr, expected + " != actual " + result);
                failures.add(expr + "  expected=" + expected + " actual=" + result);
            }
        }
        System.out.println("""
                conformance tier-1 report: total=%d pass=%d skipped-expected-error=%d fail=%d"""
                .formatted(total, pass, skip, total - pass - skip));
        for (String f : failures) System.out.println("gate6-FAIL: " + f);
        if (!failures.isEmpty()) System.exit(1);
    }

    /** alist record: ((k . v) (k . v) ...); car symbol-per-key and tail list */


    private static String rawString(Object v) {
        if (v instanceof Value.Str st) return st.value;
        if (v instanceof Value.Symbol s) return s.name;
        if (v instanceof Reader.Token t) return t.spelling();
        if (v instanceof Value.Pair p) {
            return rawString(p.car) + (p.cdr == Value.NIL ? "" : " " + rawString(p.cdr));
        }
        return String.valueOf(v);
    }

    private static void report(String verdict, String expr, String detail) {
        System.out.println("gate6[" + verdict + "] " + expr + "  =>  " + detail);
    }
}
