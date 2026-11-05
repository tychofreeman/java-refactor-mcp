package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Claude Code hooks (PreToolUse) that hold a session to the tools: {@code read} refuses reading a
 * Java source file other than through {@code inspect} (Read of a {@code .java} file, or a Bash
 * command in which sed, cat, head, tail, less, more, nl, awk or grep names one), and {@code write}
 * refuses changing one other than through the tools' refactorings and edits (Edit, Write or
 * MultiEdit of it, or a Bash command that writes it: sed -i, perl -i, tee, cp, mv, rm, or a
 * redirection). Files under a {@code resources} directory are fixtures and may be read and written
 * so. A refused call is denied, and Claude Code shows the model what to use instead.
 *
 * <p>Sessions read and wrote source by habit even when told to use the tools (gorefactor's
 * read-guard and write-guard, which these follow).
 */
final class Guard {

  private Guard() {}

  /**
   * The commands that show a file's text. grep's first operand is its pattern, unless -e or -f
   * gives it.
   */
  private static final Set<String> READERS =
      Set.of(
          "sed", "cat", "head", "tail", "less", "more", "nl", "awk", "grep", "egrep", "fgrep",
          "rg");

  /** grep's and rg's flags whose value is the next word. */
  private static final Set<String> VALUED =
      Set.of("-e", "-f", "-A", "-B", "-C", "-m", "-g", "-t", "-T", "-d", "-D");

  private static final String READ_ADVICE =
      "Read Java through inspect: a file's outline (inspect with file), lines (start_line and"
          + " end_line), a declaration by name (names: [\"Type.method\"]), or the lines that match"
          + " a regular expression, to find one (pattern, with or without file); from a shell,"
          + " bin/jrefactor inspect file=PATH pattern=REGEX. Files under a resources directory may"
          + " be read directly.";

  private static final String WRITE_ADVICE =
      "Change Java through the tools: a refactoring, or an edit (add_declaration,"
          + " replace_declaration, replace_statements, insert_statements, replace_expression,"
          + " add_case, add_element, replace_text for a few lines; create_file for a new file,"
          + " move_file for files), several as one batch; from a shell, bin/jrefactor. Where no"
          + " tool or sequence of tools makes the change, say so in docs/friction-log.md and ask"
          + " the user. Files under a resources directory may be written directly.";

  /**
   * Runs as a PreToolUse hook: {@code args[0]} is {@code read} or {@code write}. A refused call
   * gets a decision that denies it on stdout; any other call, or any failure, prints nothing, so
   * the call goes ahead.
   */
  public static void main(String[] args) {
    try {
      boolean write = args.length > 0 && args[0].equals("write");
      String why = refusal(write, new String(System.in.readAllBytes(), StandardCharsets.UTF_8));
      if (why.isEmpty()) {
        return;
      }
      ObjectNode out = SessionLog.JSON.createObjectNode();
      out.putObject("hookSpecificOutput")
          .put("hookEventName", "PreToolUse")
          .put("permissionDecision", "deny")
          .put("permissionDecisionReason", why + "\n" + (write ? WRITE_ADVICE : READ_ADVICE));
      System.out.println(out);
    } catch (IOException | RuntimeException e) {
      // A guard that fails lets the call through.
    }
  }

  /** Whether {@code path} names a Java file of the project: a .java file not under resources/. */
  static boolean javaSource(String path) {
    return path.endsWith(".java") && !("/" + path.replace('\\', '/')).contains("/resources/");
  }

  /**
   * Judges each simple command of a Bash line, with its operands resolved from {@code dir} after
   * the cds before it and with the variables the line set expanded: why the first that reads (or,
   * with {@code write}, writes) Java source does so, or "".
   */
  static String each(String line, String dir, boolean write) {
    Map<String, String> vars = new HashMap<>();
    String[] cwd = {dir};
    Function<String, String> at =
        p -> {
          for (Map.Entry<String, String> v : vars.entrySet()) {
            p =
                p.replace("${" + v.getKey() + "}", v.getValue())
                    .replace("$" + v.getKey(), v.getValue());
          }
          return cwd[0].isEmpty() || Path.of(p).isAbsolute() ? p : Path.of(cwd[0], p).toString();
        };
    for (List<String> words : commands(line)) {
      if (words.size() == 2 && words.get(0).equals("cd")) {
        cwd[0] = at.apply(words.get(1));
        continue;
      }
      int eq = words.get(0).indexOf('=');
      if (words.size() == 1
          && eq > 0
          && !words.get(0).startsWith("-")
          && !words.get(0).contains("$")) {
        vars.put(words.get(0).substring(0, eq), words.get(0).substring(eq + 1));
        continue;
      }
      String f = write ? writes(words, at) : reads(words, at);
      if (!f.isEmpty() && f.contains("$")) {
        return words.get(0)
            + (write ? " writing " : " of ")
            + f
            + " refused: a shell variable hides where the path goes, so it cannot be told from"
            + " Java source; write the path out (a file under resources/ may be read).";
      }
      if (!f.isEmpty()) {
        return words.get(0) + (write ? " writing " : " of ") + f + " refused.";
      }
    }
    return "";
  }

  /** The words after any VAR=value before the command. */
  private static List<String> command(List<String> words) {
    int i = 0;
    while (i < words.size() && words.get(i).contains("=") && !words.get(i).startsWith("-")) {
      i++;
    }
    return words.subList(i, words.size());
  }

  /** The Java source file the command reads, or "". */
  static String reads(List<String> all, Function<String, String> at) {
    List<String> words = command(all);
    if (words.isEmpty()) {
      return "";
    }
    String name = Path.of(words.get(0)).getFileName().toString();
    if (!READERS.contains(name)) {
      return "";
    }
    boolean grep = name.endsWith("grep") || name.equals("rg");
    if (grep && words.stream().anyMatch(w -> showsNoText(w, name.equals("rg")))) {
      return "";
    }
    boolean pattern = grep;
    List<String> operands = new ArrayList<>();
    for (int i = 1; i < words.size(); i++) {
      String w = words.get(i);
      if (w.contains(">")) { // a redirection: what it writes is not read
        if (w.endsWith(">")) {
          i++;
        }
      } else if (grep && (w.equals("-e") || w.equals("-f"))) {
        pattern = false;
        i++;
      } else if (grep && VALUED.contains(w)) {
        i++;
      } else if (w.startsWith("-") && !w.equals("-")) {
        // a flag
      } else if (pattern) {
        pattern = false;
      } else {
        operands.add(w);
      }
    }
    for (String o : operands) {
      if (javaSource(at.apply(o))) {
        return o;
      }
    }
    return "";
  }

  /**
   * Whether a grep flag makes it print no text of the files: a count, file names, or only its exit
   * status ({@code -c}, {@code -l}, {@code -L}, {@code -q}, alone or among other short flags). For
   * {@code rg}, {@code -L} follows links instead, so it does not count.
   */
  private static boolean showsNoText(String w, boolean rg) {
    if (w.startsWith("--")) {
      return Set.of(
              "--count", "--files-with-matches", "--files-without-match", "--quiet", "--silent")
          .contains(w);
    }
    String any = rg ? "[clq]" : "[clLq]";
    return w.startsWith("-")
        && w.length() > 1
        && w.substring(1).matches("[a-zA-Z]*" + any + "[a-zA-Z]*");
  }

  /** The Java source file the command writes, or "". */
  static String writes(List<String> all, Function<String, String> at) {
    List<String> words = command(all);
    if (words.isEmpty()) {
      return "";
    }
    String name = Path.of(words.get(0)).getFileName().toString();
    boolean inPlace = false;
    List<String> operands = new ArrayList<>();
    for (int i = 1; i < words.size(); i++) {
      String w = words.get(i);
      if (w.endsWith(">") && i + 1 < words.size()) { // a redirection into the next word
        i++;
        if (javaSource(at.apply(words.get(i)))) {
          return words.get(i);
        }
      } else if (w.contains(">")) {
        // 2>&1
      } else if (w.equals("--in-place") || w.startsWith("-i")) {
        inPlace = true;
      } else if (w.startsWith("-") && !w.startsWith("--") && w.contains("i")) {
        inPlace |= name.equals("perl"); // perl -pi
      } else if (!w.startsWith("-") || w.equals("-")) {
        operands.add(w);
      }
    }
    switch (name) {
      case "sed", "perl" -> {
        if (!inPlace) {
          return "";
        }
      }
      case "tee", "mv", "rm" -> {}
      case "cp", "install", "ln" -> {
        if (operands.isEmpty()) {
          return "";
        }
        operands = operands.subList(operands.size() - 1, operands.size()); // the destination
      }
      default -> {
        return "";
      }
    }
    for (String o : operands) {
      if (javaSource(at.apply(o))) {
        return o;
      }
    }
    return "";
  }

  /**
   * Splits a shell command line into its simple commands, each as its words with quotes removed: at
   * ;, &amp;, |, newlines, parentheses, backquotes and $(, outside quotes. &lt; counts as a space,
   * so that cat &lt; F.java names F.java; &gt;, &gt;&gt; and their forms with descriptors
   * (2&gt;&amp;1) are words of their own. It is not a shell parser, only enough to find what each
   * command is given (as gorefactor's internal/hook.Commands).
   */
  static List<List<String>> commands(String line) {
    List<List<String>> out = new ArrayList<>();
    List<String> words = new ArrayList<>();
    StringBuilder word = new StringBuilder();
    boolean inWord = false;
    char quote = 0;
    for (int i = 0; i < line.length(); i++) {
      char r = line.charAt(i);
      if (quote == '\'') {
        if (r == '\'') {
          quote = 0;
        } else {
          word.append(r);
        }
      } else if (quote == '"') {
        if (r == '"') {
          quote = 0;
        } else if (r == '\\' && i + 1 < line.length()) {
          word.append(line.charAt(++i));
        } else {
          word.append(r);
        }
      } else if (r == '\'' || r == '"') {
        quote = r;
        inWord = true;
      } else if (r == '\\' && i + 1 < line.length()) {
        word.append(line.charAt(++i));
        inWord = true;
      } else if (";&|()`\n".indexOf(r) >= 0
          || r == '$' && i + 1 < line.length() && line.charAt(i + 1) == '(') {
        inWord = endWord(words, word, inWord);
        if (!words.isEmpty()) {
          out.add(words);
          words = new ArrayList<>();
        }
      } else if (r == '>') {
        if (!inWord || !isDescriptor(word.toString())) {
          inWord = endWord(words, word, inWord);
        }
        word.append(r);
        while (i + 1 < line.length()
            && (line.charAt(i + 1) == '>'
                || line.charAt(i + 1) == '&'
                || line.charAt(i) == '&' && Character.isDigit(line.charAt(i + 1)))) {
          word.append(line.charAt(++i));
        }
        inWord = endWord(words, word, true);
      } else if (r == ' ' || r == '\t' || r == '<') {
        inWord = endWord(words, word, inWord);
      } else {
        word.append(r);
        inWord = true;
      }
    }
    endWord(words, word, inWord);
    if (!words.isEmpty()) {
      out.add(words);
    }
    return out;
  }

  /**
   * Adds the word being built to {@code words}, if there is one; false, as no word is open then.
   */
  private static boolean endWord(List<String> words, StringBuilder word, boolean inWord) {
    if (inWord) {
      words.add(word.toString());
      word.setLength(0);
    }
    return false;
  }

  /** Whether {@code s} is a file descriptor's number, or &amp;, as written before a redirection. */
  private static boolean isDescriptor(String s) {
    return s.equals("&") || !s.isEmpty() && s.chars().allMatch(Character::isDigit);
  }

  /**
   * Why the hook input {@code in} reads Java source other than through {@code inspect} (or, with
   * {@code write}, changes it other than through the tools), or "" when it does not.
   */
  static String refusal(boolean write, String in) {
    JsonNode c;
    try {
      c = SessionLog.JSON.readTree(in);
    } catch (RuntimeException e) {
      return "";
    }
    String tool = c.path("tool_name").asString("");
    JsonNode input = c.path("tool_input");
    if (tool.equals("Bash")) {
      return each(input.path("command").asString(""), c.path("cwd").asString(""), write);
    }
    String path = input.path("file_path").asString("");
    boolean judged =
        write ? Set.of("Edit", "Write", "MultiEdit").contains(tool) : tool.equals("Read");
    return judged && javaSource(path) ? tool + " of " + path + " refused." : "";
  }
}
