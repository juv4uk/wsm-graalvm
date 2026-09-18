package wsm.graalvm.providers;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.provider.TruffleLanguageProvider;
import java.util.Collection;
import java.util.List;

/** Hand-written ServiceLoader provider: bypasses the DSL APT for M0 builds. */
import com.oracle.truffle.api.TruffleLanguage;

@TruffleLanguage.Registration(
        id = "wsm", name = "WSM my-lisp",
        implementationName = "wsm-graalvm", version = "M0", defaultMimeType = "text/x-wsm",
        characterMimeTypes = { "text/x-wsm" })
public final class WsmLanguageProvider extends TruffleLanguageProvider {
    @Override protected String getLanguageClassName() { return "wsm.graalvm.WsmLanguage"; }
    @Override protected Object create() { return new wsm.graalvm.WsmLanguage(); }
    @Override protected Collection<String> getServicesClassNames() { return java.util.List.of(); }
    @Override protected java.util.List<?> createFileTypeDetectors() { return java.util.List.of(); }
}
