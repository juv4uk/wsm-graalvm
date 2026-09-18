package wsm.graalvm;

import java.util.List;

/** Reader data strings must become Lisp string values at execution/datum boundaries. */
public final class ExecutableStringContract {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static Object executeAll(List<WsmNode> nodes) {
        Object last = Value.NIL;
        for (WsmNode node : nodes) last = node.executeGeneric(null);
        return last;
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: ExecutableStringContract <semantic-registry.lisp>");
        }

        CanonRegistry registry = CanonRegistryLoader.load(args[0]);

        Object readerValue = new Reader("\"radio\"").readAll().get(0);
        require(readerValue instanceof String,
                "Reader data contract must remain Java String");

        Object executed = executeAll(
                new Compiler(registry).compileProgram(
                        new Reader("\"radio\"").readAll()));
        require(executed instanceof Value.StringValue text
                        && text.value.equals("radio"),
                "executable string must become Lisp Value.StringValue");

        Object quoted = executeAll(
                new Compiler(registry).compileProgram(
                        new Reader("(0001 \"radio\")").readAll()));
        require(quoted instanceof Value.StringValue text
                        && text.value.equals("radio"),
                "quoted string datum must become Lisp Value.StringValue");

        require(Printer.print(executed).equals("\"radio\""),
                "printer must preserve Lisp string syntax");

        System.out.println("EXECUTABLE-STRING-CONTRACT-OK");
    }
}
