;;; wsm-graalvm — Guix development manifest
;;; Usage: guix shell -m manifest.scm
;;;
;;; After entering the shell, point the build scripts at the pinned GraalVM:
;;;   export G="$GUIX_ENVIRONMENT/lib/graalvm-ce-wsm"
;;; Then:
;;;   bash scripts/fetch-third-party.sh
;;;   bash scripts/build.sh
;;;   bash scripts/run-canon.sh

(use-modules (guix packages)
             (gnu packages base)
             (gnu packages compression)
             (gnu packages curl)
             (gnu packages maven)
             (gnu packages version-control))

(define graalvm-ce-wsm
  ;; Load the package defined in the sibling guix.scm.
  (load "guix.scm"))

(packages->manifest
 (list graalvm-ce-wsm
       maven
       unzip
       curl
       git
       coreutils
       gnu-make))
