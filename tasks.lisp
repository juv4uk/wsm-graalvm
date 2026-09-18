;; wsm-graalvm task registry (swarm-node --auto-sync format)
(
 ("GRAALVM-M0-READER-SEVEN-PRIMITIVES" .
  ((priority . 9.5) (capabilities . (java truffle reader lisp canon)) (origin . my-lisp)
   (priority . 9.5)
   (description . "Reader + eval of Canon 0+7 + lambda/define/defmacro on GraalVM Truffle. Canon resolution BEFORE lexical lookup keyed by numeric semantic IDs (0001-0007). Acceptance: read my-lisp/lib/canon.lisp, evaluate (canon-conforms?) -> t. No hardcoded surface spellings.")))
)
