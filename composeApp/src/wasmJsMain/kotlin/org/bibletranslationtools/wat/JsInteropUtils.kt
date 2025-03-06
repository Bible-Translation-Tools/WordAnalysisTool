package org.bibletranslationtools.wat

@JsFun("(output) => console.log(output)")
external fun consoleLog(vararg output: JsAny?)

@JsFun("(output) => console.log(output)")
external fun consoleLog2(vararg output: String?)