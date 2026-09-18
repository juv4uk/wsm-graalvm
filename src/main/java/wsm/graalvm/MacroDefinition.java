package wsm.graalvm;

/** Macro transformer is a normal Truffle callable receiving syntax values. */
record MacroDefinition(WsmFunc transformer) {}
