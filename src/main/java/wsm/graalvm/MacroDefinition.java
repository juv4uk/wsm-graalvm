package wsm.graalvm;

import java.util.List;

record MacroDefinition(
        List<String> fixedNames,
        String restName,
        List<Object> body) {}
