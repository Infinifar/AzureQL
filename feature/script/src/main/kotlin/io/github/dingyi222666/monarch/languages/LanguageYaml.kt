// Derived from monarch-kt (LanguageYaml.kt), licensed under Apache-2.0.
// https://github.com/dingyi222666/monarch-kt
package io.github.dingyi222666.monarch.languages

import io.github.dingyi222666.monarch.common.*
 import io.github.dingyi222666.monarch.loader.dsl.*
import io.github.dingyi222666.monarch.types.IMonarchLanguage

public val YamlLanguage: IMonarchLanguage by lazy {
  buildMonarchLanguage {
    tokenPostfix = ".yaml"
    brackets {
      bracket("{","}","delimiter.bracket")
      bracket("[","]","delimiter.square")
    }
    keywords("true", "True", "TRUE", "false", "False", "FALSE", "null", "Null", "Null", "~")
    "numberInteger" and "(?:0|[+-]?[0-9]+)"
    "numberFloat" and "(?:0|[+-]?[0-9]+)(?:\\.[0-9]+)?(?:e[-+][1-9][0-9]*)?"
    "numberOctal" and "0o[0-7]+"
    "numberHex" and "0x[0-9a-fA-F]+"
    "numberInfinity" and "[+-]?\\.(?:inf|Inf|INF)"
    "numberNaN" and "\\.(?:nan|Nan|NAN)"
    "numberDate" and
        "\\d{4}-\\d\\d-\\d\\d([Tt ]\\d\\d:\\d\\d:\\d\\d(\\.\\d+)?(( ?[+-]\\d\\d?(:\\d\\d)?)|Z)?)?"
    escapes("\\\\(?:[btnfr\\\\\"']|[0-7][0-7]?|[0-3][0-7]{2})")
    tokenizer {
      root {
        include("@whitespace")
        include("@comment")
        "%[^ ]+.*${'$'}".token("meta.directive")
        "---".token("operators.directivesEnd")
        "\\.{3}".token("operators.documentEnd")
        "[-?:](?= )".token("operators")
        include("@anchor")
        include("@tagHandle")
        include("@flowCollections")
        include("@blockStyle")
        "@numberInteger(?![ \\t]*\\S+)".token("number")
        "@numberFloat(?![ \\t]*\\S+)".token("number.float")
        "@numberOctal(?![ \\t]*\\S+)".token("number.octal")
        "@numberHex(?![ \\t]*\\S+)".token("number.hex")
        "@numberInfinity(?![ \\t]*\\S+)".token("number.infinity")
        "@numberNaN(?![ \\t]*\\S+)".token("number.nan")
        "@numberDate(?![ \\t]*\\S+)".token("number.date")
        "(\".*?\"|'.*?'|[^#'\"]*?)([ \\t]*)(:)( |${'$'})".actionArray {
          token("type")
          token("white")
          token("operators")
          token("white")
        }
        include("@flowScalars")
        ".+?(?=(\\s+#|${'$'}))".action {
          cases {
            "@keywords" and "keyword"
            "@default" and "string"
          }
        }
      }
      "object" rules {
        include("@whitespace")
        include("@comment")
        "\\}".action("@brackets").state("@pop")
        ",".token("delimiter.comma")
        ":(?= )".token("operators")
        "(?:\".*?\"|'.*?'|[^,\\{\\[]+?)(?=: )".token("type")
        include("@flowCollections")
        include("@flowScalars")
        include("@tagHandle")
        include("@anchor")
        include("@flowNumber")
        "[^\\},]+".action {
          cases {
            "@keywords" and "keyword"
            "@default" and "string"
          }
        }
      }
      "array" rules {
        include("@whitespace")
        include("@comment")
        "\\]".action("@brackets").state("@pop")
        ",".token("delimiter.comma")
        include("@flowCollections")
        include("@flowScalars")
        include("@tagHandle")
        include("@anchor")
        include("@flowNumber")
        "[^\\],]+".action {
          cases {
            "@keywords" and "keyword"
            "@default" and "string"
          }
        }
      }
      "multiString" rules {
        "^( +).+${'$'}".action("string").state("@multiStringContinued.${'$'}1")
      }
      "multiStringContinued" rules {
        "^( *).+${'$'}".action {
          cases {
            "${'$'}1==${'$'}S2" and "string"
            "@default" and {
              token = "@rematch"
              next = "@popall"
            }
          }
        }
      }
      whitespace {
        "[ \\t\\r\\n]+".token("white")
      }
      comment {
        "#.*${'$'}".token("comment")
      }
      "flowCollections" rules {
        "\\[".action("@brackets").state("@array")
        "\\{".action("@brackets").state("@object")
      }
      "flowScalars" rules {
        "\"([^\"\\\\]|\\\\.)*${'$'}".token("string.invalid")
        "'([^'\\\\]|\\\\.)*${'$'}".token("string.invalid")
        "'[^']*'".token("string")
        "\"".action("string").state("@doubleQuotedString")
      }
      "doubleQuotedString" rules {
        "[^\\\\\"]+".token("string")
        "@escapes".token("string.escape")
        "\\\\.".token("string.escape.invalid")
        "\"".action("string").state("@pop")
      }
      "blockStyle" rules {
        "[>|][0-9]*[+-]?${'$'}".action("operators").state("@multiString")
      }
      "flowNumber" rules {
        "@numberInteger(?=[ \\t]*[,\\]\\}])".token("number")
        "@numberFloat(?=[ \\t]*[,\\]\\}])".token("number.float")
        "@numberOctal(?=[ \\t]*[,\\]\\}])".token("number.octal")
        "@numberHex(?=[ \\t]*[,\\]\\}])".token("number.hex")
        "@numberInfinity(?=[ \\t]*[,\\]\\}])".token("number.infinity")
        "@numberNaN(?=[ \\t]*[,\\]\\}])".token("number.nan")
        "@numberDate(?=[ \\t]*[,\\]\\}])".token("number.date")
      }
      "tagHandle" rules {
        "\\![^ ]*".token("tag")
      }
      "anchor" rules {
        "[&*][^ ]+".token("namespace")
      }
    }
  }
}
