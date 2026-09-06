// Derived from monarch-kt (LanguageShell.kt), licensed under Apache-2.0.
// https://github.com/dingyi222666/monarch-kt
package io.github.dingyi222666.monarch.languages

import io.github.dingyi222666.monarch.common.*
 import io.github.dingyi222666.monarch.loader.dsl.*
import io.github.dingyi222666.monarch.types.IMonarchLanguage

public val ShellLanguage: IMonarchLanguage by lazy {
  buildMonarchLanguage {
    tokenPostfix = ".shell"
    ignoreCase = true
    defaultToken = ""
    brackets {
      bracket("{","}","delimiter.bracket")
      bracket("(",")","delimiter.parenthesis")
      bracket("[","]","delimiter.square")
    }
    keywords("if", "then", "do", "else", "elif", "while", "until", "for", "in", "esac", "fi", "fin",
        "fil", "done", "exit", "set", "unset", "export", "function")
    "builtins" and listOf("ab", "awk", "bash", "beep", "cat", "cc", "cd", "chown", "chmod",
        "chroot", "clear", "cp", "curl", "cut", "diff", "echo", "find", "gawk", "gcc", "get", "git",
        "grep", "hg", "kill", "killall", "ln", "ls", "make", "mkdir", "openssl", "mv", "nc", "node",
        "npm", "ping", "ps", "restart", "rm", "rmdir", "sed", "service", "sh", "shopt", "shred",
        "source", "sort", "sleep", "ssh", "start", "stop", "su", "sudo", "svn", "tee", "telnet",
        "top", "touch", "vi", "vim", "wall", "wc", "wget", "who", "write", "yes", "zsh")
    "startingWithDash" and "\\-+\\w+"
    "identifiersWithDashes" and "[a-zA-Z]\\w+(?:@startingWithDash)+"
    symbols("[=><!~?&|+\\-*\\/\\^;\\.,]+")
    tokenizer {
      root {
        "@identifiersWithDashes".token("")
        "(\\s)((?:@startingWithDash)+)".actionArray {
          token("white")
          token("attribute.name")
        }
        "[a-zA-Z]\\w*".action {
          cases {
            "@keywords" and "keyword"
            "@builtins" and "type.identifier"
            "@default" and ""
          }
        }
        include("@whitespace")
        include("@strings")
        include("@parameters")
        include("@heredoc")
        "[{}\\[\\]()]".token("@brackets")
        "@symbols".token("delimiter")
        include("@numbers")
        "[,;]".token("delimiter")
      }
      whitespace {
        "\\s+".token("white")
        "(^#!.*${'$'})".token("metatag")
        "(^#.*${'$'})".token("comment")
      }
      "numbers" rules {
        "\\d*\\.\\d+([eE][\\-+]?\\d+)?".token("number.float")
        "0[xX][0-9a-fA-F_]*[0-9a-fA-F]".token("number.hex")
        "\\d+".token("number")
      }
      "strings" rules {
        "'".action("string").state("@stringBody")
        "\"".action("string").state("@dblStringBody")
      }
      "stringBody" rules {
        "'".action("string").state("@popall")
        ".".token("string")
      }
      "dblStringBody" rules {
        "\"".action("string").state("@popall")
        ".".token("string")
      }
      "heredoc" rules {
        "(<<[-<]?)(\\s*)(['\"`]?)([\\w\\-]+)(['\"`]?)".actionArray {
          token("constants")
          token("white")
          token("string.heredoc.delimiter")
          token("string.heredoc")
          token("string.heredoc.delimiter")
        }
      }
      "parameters" rules {
        "\\${'$'}\\d+".token("variable.predefined")
        "\\${'$'}\\w+".token("variable")
        "\\${'$'}[*@#?\\-${'$'}!0_]".token("variable")
        "\\${'$'}'".action("variable").state("@parameterBodyQuote")
        "\\${'$'}\"".action("variable").state("@parameterBodyDoubleQuote")
        "\\${'$'}\\(".action("variable").state("@parameterBodyParen")
        "\\${'$'}\\{".action("variable").state("@parameterBodyCurlyBrace")
      }
      "parameterBodyQuote" rules {
        "[^#:%*@\\-!_']+".token("variable")
        "[#:%*@\\-!_]".token("delimiter")
        "[']".action("variable").state("@pop")
      }
      "parameterBodyDoubleQuote" rules {
        "[^#:%*@\\-!_\"]+".token("variable")
        "[#:%*@\\-!_]".token("delimiter")
        "[\"]".action("variable").state("@pop")
      }
      "parameterBodyParen" rules {
        "[^#:%*@\\-!_)]+".token("variable")
        "[#:%*@\\-!_]".token("delimiter")
        "[)]".action("variable").state("@pop")
      }
      "parameterBodyCurlyBrace" rules {
        "[^#:%*@\\-!_}]+".token("variable")
        "[#:%*@\\-!_]".token("delimiter")
        "[}]".action("variable").state("@pop")
      }
    }
  }
}
