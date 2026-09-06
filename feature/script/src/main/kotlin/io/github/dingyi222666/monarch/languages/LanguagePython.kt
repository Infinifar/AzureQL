// Derived from monarch-kt (LanguagePython.kt), licensed under Apache-2.0.
// https://github.com/dingyi222666/monarch-kt
package io.github.dingyi222666.monarch.languages

import io.github.dingyi222666.monarch.common.*
 import io.github.dingyi222666.monarch.loader.dsl.*
import io.github.dingyi222666.monarch.types.IMonarchLanguage

public val PythonLanguage: IMonarchLanguage by lazy {
  buildMonarchLanguage {
    tokenPostfix = ".python"
    defaultToken = ""
    brackets {
      bracket("{","}","delimiter.curly")
      bracket("[","]","delimiter.bracket")
      bracket("(",")","delimiter.parenthesis")
    }
    keywords("False", "None", "True", "_", "and", "as", "assert", "async", "await", "break", "case",
        "class", "continue", "def", "del", "elif", "else", "except", "exec", "finally", "for",
        "from", "global", "if", "import", "in", "is", "lambda", "match", "nonlocal", "not", "or",
        "pass", "print", "raise", "return", "try", "type", "while", "with", "yield", "int", "float",
        "long", "complex", "hex", "abs", "all", "any", "apply", "basestring", "bin", "bool",
        "buffer", "bytearray", "callable", "chr", "classmethod", "cmp", "coerce", "compile",
        "complex", "delattr", "dict", "dir", "divmod", "enumerate", "eval", "execfile", "file",
        "filter", "format", "frozenset", "getattr", "globals", "hasattr", "hash", "help", "id",
        "input", "intern", "isinstance", "issubclass", "iter", "len", "locals", "list", "map",
        "max", "memoryview", "min", "next", "object", "oct", "open", "ord", "pow", "print",
        "property", "reversed", "range", "raw_input", "reduce", "reload", "repr", "reversed",
        "round", "self", "set", "setattr", "slice", "sorted", "staticmethod", "str", "sum", "super",
        "tuple", "type", "unichr", "unicode", "vars", "xrange", "zip", "__dict__", "__methods__",
        "__members__", "__class__", "__bases__", "__name__", "__mro__", "__subclasses__",
        "__init__", "__import__")
    tokenizer {
      root {
        include("@whitespace")
        include("@numbers")
        include("@strings")
        "[,:;]".token("delimiter")
        "[{}\\[\\]()]".token("@brackets")
        "@[a-zA-Z_]\\w*".token("tag")
        "[a-zA-Z_]\\w*".action {
          cases {
            "@keywords" and "keyword"
            "@default" and "identifier"
          }
        }
      }
      whitespace {
        "\\s+".token("white")
        "(^#.*${'$'})".token("comment")
        "'''".action("string").state("@endDocString")
        "\"\"\"".action("string").state("@endDblDocString")
      }
      "endDocString" rules {
        "[^']+".token("string")
        "\\\\'".token("string")
        "'''".action("string").state("@popall")
        "'".token("string")
      }
      "endDblDocString" rules {
        "[^\"]+".token("string")
        "\\\\\"".token("string")
        "\"\"\"".action("string").state("@popall")
        "\"".token("string")
      }
      "numbers" rules {
        "-?0x([abcdef]|[ABCDEF]|\\d)+[lL]?".token("number.hex")
        "-?(\\d*\\.)?\\d+([eE][+\\-]?\\d+)?[jJ]?[lL]?".token("number")
      }
      "strings" rules {
        "'${'$'}".action("string.escape").state("@popall")
        "'".action("string.escape").state("@stringBody")
        "\"${'$'}".action("string.escape").state("@popall")
        "\"".action("string.escape").state("@dblStringBody")
      }
      "stringBody" rules {
        "[^\\\\']+${'$'}".action("string").state("@popall")
        "[^\\\\']+".token("string")
        "\\\\.".token("string")
        "'".action("string.escape").state("@popall")
        "\\\\${'$'}".token("string")
      }
      "dblStringBody" rules {
        "[^\\\\\"]+${'$'}".action("string").state("@popall")
        "[^\\\\\"]+".token("string")
        "\\\\.".token("string")
        "\"".action("string.escape").state("@popall")
        "\\\\${'$'}".token("string")
      }
    }
  }
}
