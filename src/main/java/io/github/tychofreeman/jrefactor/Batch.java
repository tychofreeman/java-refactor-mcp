package io.github.tychofreeman.jrefactor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Several tool calls run in order as one change: all of them, or none. */
final class Batch {

  private Batch() {}

  static String run(SourceProject project, Args args) throws Exception {
    List<Map<String, Object>> steps = args.objects("steps");
    if (steps.isEmpty()) {
      throw new Refusal("batch needs steps: a list of {\"tool\": ..., \"args\": {...}}.");
    }
    String check = args.string("check") == null ? "each" : args.string("check");
    if (!check.equals("each") && !check.equals("end")) {
      throw new Refusal("check must be \"each\" or \"end\"; got " + check + ".");
    }
    if (check.equals("end")) {
      Map<String, Integer> before = TypeCheck.errors(project);
      String said = Gate.deferred(() -> steps(project, steps, true));
      List<String> introduced = TypeCheck.introduced(before, TypeCheck.errors(project));
      if (!introduced.isEmpty()) {
        throw new Refusal(
            "the batch would add compile errors, so nothing was changed:\n  "
                + String.join("\n  ", introduced)
                + "\nAdd the steps that fix them, or change the steps that cause them.");
      }
      return said + "\nchecked at the end: no new compile errors";
    }
    return steps(project, steps, false);
  }

  /**
   * The lines one step changed in one file: old lines a-b became new lines c-d (an empty range has
   * its end before its start). refStart-refEnd is the new range without blank lines at its ends,
   * which a reference names.
   */
  private record Region(
      String file, int oldStart, int oldEnd, int newStart, int newEnd, int refStart, int refEnd) {}

  /** A step that ran: its number, its id, and the region it changed, if any. */
  private record Done(int number, String id, Region region) {}

  private static final Set<String> LINE_ARGS = Set.of("line", "start_line", "end_line");

  private static final Pattern REFERENCE =
      Pattern.compile("\\$(prev|\\w+)\\.(start|end)([+-]\\d+)?");

  private static String steps(SourceProject project, List<Map<String, Object>> steps, boolean atEnd)
      throws Exception {
    List<Done> done = new ArrayList<>();
    StringBuilder said = new StringBuilder("batch of " + steps.size() + " steps:");
    for (int i = 0; i < steps.size(); i++) {
      int number = i + 1;
      Map<String, Object> step = steps.get(i);
      Object tool = step.get("tool");
      if (!(tool instanceof String name)) {
        throw new Refusal("step " + number + " has no tool.");
      }
      if (name.equals("batch") || name.equals("undo")) {
        throw new Refusal("step " + number + ": " + name + " cannot be a step of a batch.");
      }
      Tools.Def def = Tools.named(name);
      Map<String, Object> stepArgs = new LinkedHashMap<>();
      if (step.get("args") instanceof Map<?, ?> given) {
        given.forEach((k, v) -> stepArgs.put(String.valueOf(k), v));
      }
      Object file = stepArgs.get("file");
      String rel = file instanceof String s ? s : null;
      for (String key : LINE_ARGS) {
        if (stepArgs.containsKey(key)) {
          stepArgs.put(key, line(stepArgs.get(key), key, rel, done, number));
        }
      }
      Path path = rel == null ? null : project.file(rel);
      String before = path != null && Files.isRegularFile(path) ? Files.readString(path) : null;
      String text;
      try {
        text = Tools.call(def, project, stepArgs);
      } catch (Refusal refusal) {
        String hint =
            !atEnd && refusal.getMessage().contains("would add compile errors")
                ? "\nIf a later step fixes these errors, send the batch with check \"end\"."
                : "";
        throw new Refusal(
            "step "
                + number
                + " of "
                + steps.size()
                + " ("
                + name
                + ") refused: "
                + refusal.getMessage()
                + "\nNothing was changed."
                + hint);
      }
      String after = path != null && Files.isRegularFile(path) ? Files.readString(path) : null;
      Object id = step.get("id");
      done.add(new Done(number, id instanceof String s ? s : null, region(rel, before, after)));
      said.append("\n").append(number).append(". ").append(text);
    }
    return said.toString();
  }

  /**
   * A line argument as the step must get it: a number is a line as read before the batch, moved
   * past earlier steps; "$prev.start", "$2.end" or "$id.start+1" is a line an earlier step changed.
   */
  private static Object line(Object value, String key, String rel, List<Done> done, int number) {
    if (value instanceof Number n) {
      return translate(n.intValue(), rel, done, 0, number);
    }
    if (!(value instanceof String ref) || !ref.startsWith("$")) {
      return value;
    }
    Matcher m = REFERENCE.matcher(ref);
    if (!m.matches()) {
      throw new Refusal(
          "step "
              + number
              + ": "
              + key
              + " "
              + ref
              + " is not a reference such as \"$prev.end+1\".");
    }
    String which = m.group(1);
    int index = -1;
    for (int j = done.size() - 1; j >= 0; j--) {
      Done d = done.get(j);
      boolean named =
          which.equals("prev")
              ? j == done.size() - 1
              : which.equals(String.valueOf(d.number())) || which.equals(d.id());
      if (named) {
        index = j;
        break;
      }
    }
    if (index < 0) {
      throw new Refusal("step " + number + ": " + ref + " names no earlier step.");
    }
    Region r = done.get(index).region();
    if (r == null || !r.file().equals(rel)) {
      throw new Refusal(
          "step "
              + number
              + ": "
              + ref
              + " names step "
              + done.get(index).number()
              + ", which changed no lines of "
              + rel
              + ".");
    }
    int base = m.group(2).equals("start") ? r.refStart() : r.refEnd();
    int offset = m.group(3) == null ? 0 : Integer.parseInt(m.group(3).replace("+", ""));
    return translate(base + offset, rel, done, index + 1, number);
  }

  /** {@code line}, as of before step {@code from}, moved past that step and the ones after it. */
  private static int translate(int line, String rel, List<Done> done, int from, int number) {
    int at = line;
    for (int j = from; j < done.size(); j++) {
      Region r = done.get(j).region();
      if (r == null || !r.file().equals(rel) || at < r.oldStart()) {
        continue;
      }
      int oldLen = r.oldEnd() - r.oldStart() + 1;
      int newLen = r.newEnd() - r.newStart() + 1;
      if (at > r.oldEnd()) {
        at += newLen - oldLen;
      } else if (oldLen == newLen) {
        at = at - r.oldStart() + r.newStart();
      } else {
        throw new Refusal(
            "line "
                + line
                + " of "
                + rel
                + " was rewritten by step "
                + done.get(j).number()
                + ", so its number no longer says which line step "
                + number
                + " means. Name a line that step changed (\"$"
                + done.get(j).number()
                + ".start\"), or put this step before it.");
      }
    }
    return at;
  }

  /** The lines that differ between {@code before} and {@code after}, between their common ends. */
  private static Region region(String rel, String before, String after) {
    if (rel == null || after == null || after.equals(before)) {
      return null;
    }
    String[] old = before == null ? new String[0] : before.split("\n", -1);
    String[] now = after.split("\n", -1);
    int prefix = 0;
    while (prefix < old.length && prefix < now.length && old[prefix].equals(now[prefix])) {
      prefix++;
    }
    int suffix = 0;
    while (suffix < old.length - prefix
        && suffix < now.length - prefix
        && old[old.length - 1 - suffix].equals(now[now.length - 1 - suffix])) {
      suffix++;
    }
    int newStart = prefix + 1;
    int newEnd = now.length - suffix;
    int refStart = newStart;
    int refEnd = newEnd;
    while (refStart <= refEnd && now[refStart - 1].isBlank()) {
      refStart++;
    }
    while (refEnd >= refStart && now[refEnd - 1].isBlank()) {
      refEnd--;
    }
    return new Region(rel, prefix + 1, old.length - suffix, newStart, newEnd, refStart, refEnd);
  }
}
