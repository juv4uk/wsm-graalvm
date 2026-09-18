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
        defaultMimeType = "text/x-wsm"
)
public final class WsmLanguage extends TruffleLanguage<Void> {

    @Override
    protected Void createContext(com.oracle.truffle.api.TruffleLanguage.Env env) {
        return null;
    }

    @Override
    protected org.graalvm.options.OptionDescriptors getOptionDescriptors() {
        return new WsmOptionDescriptors();
    }

    @Override
    protected CallTarget parse(com.oracle.truffle.api.TruffleLanguage.ParsingRequest request)
            throws IOException {
        String code = request.getSource().getCharacters().toString();
        String registryPath = System.getProperty("wsm.registryPath");
        if (registryPath == null) {
            throw new IllegalArgumentException(
                    "missing system property wsm.registryPath (numeric registry authority)");
        }
        CanonRegistry registry = CanonRegistryLoader.load(registryPath);
        Compiler compiler = new Compiler(registry, this);
        List<WsmNode> forms = compiler.compileProgram(new Reader(code).readAll());
        ProgramBodyNode body = new ProgramBodyNode(forms);
        BodyRoot root = new BodyRoot(this, body);
        return root.getCallTarget();
    }

    private List<Object> formsOf(String code) {
        return new Reader(code).readAll();
    }

    static final class BodyRoot extends RootNode {
        private final ProgramBodyNode body;
        BodyRoot(WsmLanguage language, ProgramBodyNode body) {
            super(language);
            this.body = body;
        }
        @Override
        public Object execute(VirtualFrame frame) {
            Object last = body.run(frame);
            System.out.println("[wsm-graalvm M0] result: " + Printer.print(last));
            return last;
        }
    }
}
