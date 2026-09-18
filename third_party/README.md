third_party — jars consumed by the substrate runtime
- truffle-api-25.3.4.1.jar + polyglot-25.3.4.1.jar + truffle-runtime-25.3.4.1.jar
  from Maven Central, version-matched to the GraalVMLS CE 25.3.4.1 host
- truffle-compiler.jar is copied verbatim from the GraalVM distribution's own
  lib/truffle (the built-in libgraal path avoids version mismatch)
- nativeimage.jar / graalvm-collections.jar extracted from the same
  distribution's jmods (the modules ship as // jmod-only since GraalVM 24)
The older third-party stubs removed in this commit are gone.
