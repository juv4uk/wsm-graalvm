package wsm.graalvm;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * WSM my-lisp substrate M0 on GraalVM Truffle.
 *
 * System property wsm.registryPath holds the path to the numeric semantic
 * registry (lib/surface/semantic-registry.lisp) — the only authority for
 * surface spellings, so the substrate never hardcodes Lisp words.
 */
@TruffleLanguage.Registration(
        id = "wsm",
        name = "WSM my-lisp",
        implementationName = "wsm-graalvm",
        version = "M0",
        defaultMimeType = "text/x-wsm",
        characterMimeTypes = { "text/x-wsm" }
)
public final class WsmLanguage extends TruffleLanguage<WsmContext> {
    private static final ContextReference<WsmContext> CONTEXT_REFERENCE =
            ContextReference.create(WsmLanguage.class);

    @Override
    protected WsmContext createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return new WsmContext(System.getProperty("wsm.registryPath"));
    }

    @Override
    protected void initializeContext(WsmContext context) {
        context.initialize();
    }

    @Override
    protected org.graalvm.options.OptionDescriptors getOptionDescriptors() {
        return new WsmOptionDescriptors();
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request)
            throws IOException {
        String code = request.getSource().getCharacters().toString();
        WsmContext context = CONTEXT_REFERENCE.get(null);
        List<Object> forms = new Reader(code).readAll();
        BodyRoot root = new BodyRoot(
                this,
                context.registry(),
                context.globals(),
                forms);
        return root.getCallTarget();
    }

    private List<Object> formsOf(String code) {
        return new Reader(code).readAll();
    }

    static final class BodyRoot extends RootNode {
        private final WsmLanguage language;
        private final CanonRegistry registry;
        private final GlobalBindings globals;
        private final List<Object> forms;

        BodyRoot(
                WsmLanguage language,
                CanonRegistry registry,
                GlobalBindings globals,
                List<Object> forms) {
            super(language);
            this.language = language;
            this.registry = registry;
            this.globals = globals;
            this.forms = List.copyOf(forms);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Compiler compiler = new Compiler(registry, language, globals);
            Object last = Value.NIL;

            // Bootstrap semantics are sequential: an earlier top-level
            // definition is executed before a later form is compiled. This
            // lets Lisp-owned functions participate in later macro expansion
            // without promoting them into host/compiler authority.
            for (Object form : forms) {
                WsmNode node = compiler.compile(form, compiler.root());
                last = node.executeGeneric(frame);
            }

            System.out.println("[wsm-graalvm M0] result: " + Printer.print(last));
            return last;
        }
    }
}
