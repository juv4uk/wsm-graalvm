;; Extracts Canon 0+7 surface rows (numeric IDs only) from the real
;; registry authority and emits key=value lines for the Truffle M0 reader.
;; Executed by my-lisp (the Rust oracle), so no table duplication here.
(load "third_party_gen/accum.lisp" t)
