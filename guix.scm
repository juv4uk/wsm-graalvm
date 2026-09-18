;;; wsm-graalvm — Guix package definition for the pinned GraalVM toolchain
;;; This file is read by `guix shell' (auto-detected) or loaded from manifest.scm.

(use-modules (guix packages)
             (guix download)
             (guix build-system trivial)
             (guix gexp)
             (guix licenses))

(package
  (name "graalvm-ce-wsm")
  (version "25.3.4.1")
  (source (origin
            (method url-fetch)
            (uri "file:///home/agents/graalvm-jdk.tar.gz")
            (sha256
             (base32
              "1ivgic99j5jlc4xp0zbjjapnzjbjq4ygmvnh8kmjc50lqk83ig5j"))))
  (build-system trivial-build-system)
  (arguments
   (list
    #:modules '((guix build utils))
    #:builder
    #~(begin
        (use-modules (guix build utils))
        (let* ((source (assoc-ref %build-inputs "source"))
               (out (assoc-ref %outputs "out"))
               (target (string-append out "/lib/graalvm-ce-wsm"))
               (unpack-dir (string-append out "/unpack"))
               (tar (string-append (assoc-ref %build-inputs "tar") "/bin/tar"))
               (gzip-bin (string-append (assoc-ref %build-inputs "gzip") "/bin/gzip")))
          (setenv "PATH" (string-append (assoc-ref %build-inputs "gzip") "/bin:" (getenv "PATH")))
          (mkdir-p unpack-dir)
          (invoke tar "-xzf" source "-C" unpack-dir)
          (mkdir-p (dirname target))
          (rename-file (string-append unpack-dir "/graalvm-community-25.3.4.1+1.1")
                       target)
          #t))))
  (inputs (list tar gzip))
  (synopsis "GraalVM CE toolchain pinned for wsm-graalvm")
  (description
   "Pinned GraalVM Community Edition 25.3.4.1 JDK used as the substrate
compiler for the wsm-graalvm Truffle witness.  This is a binary distribution
unpack; see GraalVM Free Terms and Conditions for licensing.")
  (home-page "https://www.graalvm.org/")
  (license (non-copyleft
            "https://www.oracle.com/downloads/licenses/graalvm-ftf-se-license.html")))
