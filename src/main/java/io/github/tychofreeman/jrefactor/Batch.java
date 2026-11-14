package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.ObjectMapper;

/** Several tool calls run in order as one change: all of them, or none. */
final class Batch {

  private Batch() {}

  static Outcome run(SourceProject project, Args args) throws Exception {
    Session session = project.session();
    List<Map<String, Object>> given = args.objects("steps");
    if (given.isEmpty()) {
      throw new Refusal("batch needs steps: a list of {\"tool\": ..., \"args\": {...}}.");
    }
    List<Map<String, Object>> steps = given;
    String check = args.string("check");
    if (args.flag("retry")) {
      if (session.refusedBatch == null) {
        throw new Refusal(
            "retry resends the last refused batch, and there is no refused batch since the last"
                + " one passed; send the whole batch.");
      }
      steps = retried(session.refusedBatch, given);
      check = check == null ? session.refusedCheck : check;
    }
    String mode = check == null ? "each" : check;
    if (!mode.equals("each") && !mode.equals("end")) {
      throw new Refusal("check must be \"each\" or \"end\"; got " + mode + ".");
    }
    Lifted lifted = underArgs(steps, session);
    List<Map<String, Object>> run = lifted.steps();
    try {
      Outcome outcome;
      if (mode.equals("end")) {
        Gate.Checked<String> checked =
            Gate.checkedAtEnd(
                project,
                () -> steps(project, run, true),
                "the batch",
                "Add the steps that fix them, or change the steps that cause them.");
        outcome =
            new Outcome(
                checked.result() + "\nchecked at the end: no new compile errors" + lifted.note(),
                checked.declared());
      } else {
        outcome = Outcome.of(steps(project, run, false) + lifted.note());
      }
      session.refusedBatch = null;
      return outcome;
    } catch (Refusal refusal) {
      session.refusedBatch = run;
      session.refusedCheck = mode;
      throw new Refusal(
          refusal.getMessage()
              + "\nTo fix it without resending the rest, batch with retry: true and only the steps"
              + " that change, by their number here: {\"step\": 2, \"args\": {...}} replaces step"
              + " 2 (with \"tool\" to change its tool), {\"after\": 2, \"tool\": ..., \"args\":"
              + " {...}} adds one after it, {\"remove\": 2} drops it; remove and after move a step.");
    }
  }

  /**
   * The steps of {@code base}, the refused batch, with {@code changes} made: {@code step} N
   * replaces step N (keeping its tool when the change names none), {@code after} N adds a step
   * after step N (0 for first), {@code remove} N drops step N; a remove and an after move a step.
   * Numbers are those of {@code base}.
   */
  static List<Map<String, Object>> retried(
      List<Map<String, Object>> base, List<Map<String, Object>> changes) {
    List<Map<String, Object>> replaced = new ArrayList<>(base);
    java.util.Set<Integer> removed = new java.util.HashSet<>();
    java.util.Map<Integer, List<Map<String, Object>>> added = new java.util.HashMap<>();
    for (Map<String, Object> c : changes) {
      Map<String, Object> step = new LinkedHashMap<>(c);
      Object replace = step.remove("step");
      Object after = step.remove("after");
      Object remove = step.remove("remove");
      int kinds = (replace == null ? 0 : 1) + (after == null ? 0 : 1) + (remove == null ? 0 : 1);
      if (kinds != 1) {
        throw new Refusal(
            "each step of a retry has one of step (the number of the one it replaces), after (the"
                + " number of the one it follows) or remove (the number of the one it drops).");
      }
      int n = ((Number) (replace != null ? replace : after != null ? after : remove)).intValue();
      // A step numbered one past the last is a new last step, as after the last one.
      boolean appended = replace != null && n == base.size() + 1;
      if (n < (after != null ? 0 : 1) || (n > base.size() && !appended)) {
        throw new Refusal(
            "the refused batch has steps 1 to "
                + base.size()
                + "; "
                + n
                + " is none of them (step "
                + (base.size() + 1)
                + " adds one at the end).");
      }
      if (remove != null) {
        removed.add(n);
      } else if (appended) {
        if (!step.containsKey("tool")) {
          throw new Refusal("step " + n + " is a new step, so it needs its tool.");
        }
        added.computeIfAbsent(base.size(), k -> new ArrayList<>()).add(step);
      } else if (replace != null) {
        if (!step.containsKey("tool")) {
          step.put("tool", base.get(n - 1).get("tool"));
        }
        replaced.set(n - 1, step);
      } else {
        added.computeIfAbsent(n, k -> new ArrayList<>()).add(step);
      }
    }
    List<Map<String, Object>> out = new ArrayList<>(added.getOrDefault(0, List.of()));
    for (int i = 1; i <= base.size(); i++) {
      if (!removed.contains(i)) {
        out.add(replaced.get(i - 1));
      }
      out.addAll(added.getOrDefault(i, List.of()));
    }
    return out;
  }

  /**
   * The lines one step changed in one file: old lines a-b became new lines c-d (an empty range has
   * its end before its start), between the lines it left alone at both ends. refStart-refEnd is the
   * new range without blank lines at its ends, which a reference names. {@code hunks} are the runs
   * of changed lines within it, in order, so a line between two of them (shifted by an import and a
   * method at the end) still has a number.
   */
  private record Region(
      String file,
      int oldStart,
      int oldEnd,
      int newStart,
      int newEnd,
      int refStart,
      int refEnd,
      List<Hunk> hunks) {}

  /** A step that ran: its number, its id, and the region it changed, if any. */
  private record Done(int number, String id, Region region) {}

  private static final Set<String> LINE_ARGS = Set.of("line", "start_line", "end_line");

  private static final Pattern REFERENCE =
      Pattern.compile("\\$(prev|\\w+)\\.(start|end)([+-]\\d+)?");

  /** Above this many line pairs, a region's middle is one hunk rather than diffed. */
  private static final long DIFF_LIMIT = 4_000_000L;

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The keys a step takes itself; its tool's arguments go under {@code args}. */
  private static final Set<String> STEP_KEYS = Set.of("tool", "args", "id");

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
      Tools.Def def = Tools.named(name, project.session());
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
        text = Tools.call(def, project, stepArgs).render();
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
      if (r == null || !r.file().equals(rel)) {
        continue;
      }
      int shift = 0;
      boolean within = false;
      for (Hunk h : r.hunks()) {
        if (at < h.oldStart()) {
          break;
        }
        int oldLen = h.oldEnd() - h.oldStart() + 1;
        int newLen = h.newEnd() - h.newStart() + 1;
        if (at > h.oldEnd()) {
          shift += newLen - oldLen;
          continue;
        }
        if (oldLen != newLen) {
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
        at = at - h.oldStart() + h.newStart();
        within = true;
        break;
      }
      if (!within) {
        at += shift;
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
    return new Region(
        rel,
        prefix + 1,
        old.length - suffix,
        newStart,
        newEnd,
        refStart,
        refEnd,
        hunks(old, now, prefix, old.length - suffix, now.length - suffix));
  }

  /**
   * A run of changed lines: old lines a-b became new lines c-d; an empty range ends before it
   * starts.
   */
  private record Hunk(int oldStart, int oldEnd, int newStart, int newEnd) {}

  /**
   * The runs of lines that differ between {@code old[from..oldTo)} and {@code now[from..nowTo)}, as
   * 1-based lines, by a longest common subsequence of lines.
   */
  private static List<Hunk> hunks(String[] old, String[] now, int from, int oldTo, int nowTo) {
    int m = oldTo - from;
    int n = nowTo - from;
    if ((long) m * n > DIFF_LIMIT) {
      return List.of(new Hunk(from + 1, oldTo, from + 1, nowTo));
    }
    int[][] common = new int[m + 1][n + 1];
    for (int i = m - 1; i >= 0; i--) {
      for (int j = n - 1; j >= 0; j--) {
        common[i][j] =
            old[from + i].equals(now[from + j])
                ? common[i + 1][j + 1] + 1
                : Math.max(common[i + 1][j], common[i][j + 1]);
      }
    }
    List<Hunk> out = new ArrayList<>();
    int i = 0;
    int j = 0;
    int openI = -1;
    int openJ = -1;
    while (i < m || j < n) {
      boolean same = i < m && j < n && old[from + i].equals(now[from + j]);
      if (same) {
        if (openI >= 0) {
          out.add(new Hunk(from + openI + 1, from + i, from + openJ + 1, from + j));
          openI = -1;
        }
        i++;
        j++;
        continue;
      }
      if (openI < 0) {
        openI = i;
        openJ = j;
      }
      if (j >= n || (i < m && common[i + 1][j] >= common[i][j + 1])) {
        i++;
      } else {
        j++;
      }
    }
    if (openI >= 0) {
      out.add(new Hunk(from + openI + 1, from + m, from + openJ + 1, from + n));
    }
    return out;
  }

  /** A refused batch as {@link #save} writes it: its steps and the check it ran with. */
  record Refused(String check, List<Map<String, Object>> steps) {}

  /**
   * Writes {@code project}'s refused batch to {@code file}, or deletes the file when there is none,
   * so {@code retry} in a later process can {@link #load} it: the command line runs one call per
   * process.
   */
  static void save(SourceProject project, Path file) throws IOException {
    Session session = project.session();
    if (session.refusedBatch == null) {
      Files.deleteIfExists(file);
      return;
    }
    Files.createDirectories(file.getParent());
    Files.writeString(
        file, JSON.writeValueAsString(new Refused(session.refusedCheck, session.refusedBatch)));
  }

  /** Makes the batch {@link #save} wrote to {@code file} {@code project}'s refused batch. */
  static void load(SourceProject project, Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return;
    }
    Refused refused = JSON.readValue(Files.readString(file), Refused.class);
    project.session().refusedBatch = refused.steps();
    project.session().refusedCheck = refused.check();
  }

  /**
   * Steps with the arguments their tool takes moved from beside {@code tool} to under {@code args},
   * and the numbers of the steps that had any there. A step whose tool is unknown is left to the
   * run, whose refusal lists the tools.
   */
  private static Lifted underArgs(List<Map<String, Object>> steps, Session session) {
    List<Map<String, Object>> lifted = new ArrayList<>();
    List<String> moved = new ArrayList<>();
    for (int i = 0; i < steps.size(); i++) {
      Map<String, Object> step = steps.get(i);
      lifted.add(step);
      if (!(step.get("tool") instanceof String name)) {
        continue;
      }
      Tools.Def def;
      try {
        def = Tools.named(name, session);
      } catch (Refusal unknown) {
        continue;
      }
      List<String> beside =
          step.keySet().stream().filter(k -> !STEP_KEYS.contains(k) && takes(def, k)).toList();
      if (beside.isEmpty()) {
        continue;
      }
      Map<String, Object> args = new LinkedHashMap<>();
      if (step.get("args") instanceof Map<?, ?> given) {
        given.forEach((k, v) -> args.put(String.valueOf(k), v));
      }
      for (String key : beside) {
        if (args.containsKey(key) && !Objects.equals(args.get(key), step.get(key))) {
          throw new Refusal(
              "step "
                  + (i + 1)
                  + " ("
                  + name
                  + ") gives "
                  + key
                  + " both beside \"tool\" and under \"args\", differently; send it once, under"
                  + " \"args\". Nothing was changed.");
        }
        args.put(key, step.get(key));
      }
      Map<String, Object> copy = new LinkedHashMap<>();
      step.forEach((k, v) -> {
        if (!beside.contains(k)) {
          copy.put(k, v);
        }
      });
      copy.put("args", args);
      lifted.set(i, copy);
      moved.add(String.valueOf(i + 1));
    }
    return new Lifted(lifted, moved);
  }

  /** Whether {@code def} takes an argument named {@code key}, by its name or an old one. */
  private static boolean takes(Tools.Def def, String key) {
    return def.args().contains(key)
        || Tools.ARG_ALIASES.getOrDefault(def.name(), Map.of()).containsKey(key)
        || Tools.RETIRED_ARGS.getOrDefault(def.name(), Set.of()).contains(key);
  }

  /** "a", "a and b", "a, b and c". */
  private static String listed(List<String> items) {
    int last = items.size() - 1;
    return last == 0
        ? items.get(0)
        : String.join(", ", items.subList(0, last)) + " and " + items.get(last);
  }

  /**
   * Steps with their arguments under {@code args}, and the numbers of those that had them beside.
   */
  private record Lifted(List<Map<String, Object>> steps, List<String> moved) {
    /** The result's line naming the steps whose arguments were beside {@code tool}, or nothing. */
    String note() {
      if (moved.isEmpty()) {
        return "";
      }
      return moved.size() == 1
          ? "\nstep " + moved.get(0) + ": its arguments beside \"tool\" were read as its \"args\"."
          : "\nsteps "
              + listed(moved)
              + ": their arguments beside \"tool\" were read as their \"args\".";
    }
  }
}
