package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The roadmap's items, one file each under docs/roadmap/: well formed, no number taken twice. */
class RoadmapTest {

  private static final Path ROADMAP = Path.of("docs/roadmap");

  /** The header, then the row's three parts, as bin/roadmap reads them. */
  private static final Pattern ITEM =
      Pattern.compile(
          "---\norder: (none|[1-9][0-9]*)\nitem: ([1-9][0-9]*)\n"
              + "status: (Open|Partly done|Done|Closed)\n---\n\n"
              + "## Item\n\n.+\n\n## Status\n\n.+\n\n## Evidence\n\n.*",
          Pattern.DOTALL);

  @Test
  void everyItemIsWellFormedAndNoNumberIsTakenTwice() throws IOException {
    Map<String, String> files = new TreeMap<>();
    try (Stream<Path> list = Files.list(ROADMAP)) {
      for (Path p : list.toList()) {
        files.put(p.getFileName().toString(), Files.readString(p));
      }
    }
    assertEquals(List.of(), problems(files));
  }

  @Test
  void aClashOrAMalformedFileIsNamed() {
    String item =
        "---\norder: %s\nitem: %s\nstatus: Open\n---\n\n"
            + "## Item\n\nA title\n\n## Status\n\nOpen\n\n## Evidence\n\n";
    Map<String, String> files = new TreeMap<>();
    files.put("7-one.md", item.formatted("3", "7"));
    files.put("7-two.md", item.formatted("4", "7"));
    files.put("8-three.md", item.formatted("3", "8"));
    files.put("9-four.md", item.formatted("5", "10"));
    files.put("11-five.md", "order: 6\nitem: 11\n");
    assertEquals(
        List.of(
            "11-five.md: the header is not order, item and status between --- lines,"
                + " then ## Item, ## Status and ## Evidence",
            "9-four.md: the name starts with 9, the header says item 10",
            "item 7 is taken by 7-one.md and 7-two.md",
            "order 3 is taken by 7-one.md and 8-three.md"),
        problems(files));
  }

  /** What is wrong with these files (name to text): each malformed file, then each clash. */
  static List<String> problems(Map<String, String> files) {
    List<String> problems = new ArrayList<>();
    Map<String, List<String>> orders = new TreeMap<>();
    Map<String, List<String>> items = new TreeMap<>();
    for (Map.Entry<String, String> f : new TreeMap<>(files).entrySet()) {
      String name = f.getKey();
      Matcher m = ITEM.matcher(f.getValue());
      if (!name.endsWith(".md") || !m.matches()) {
        problems.add(
            name
                + ": the header is not order, item and status between --- lines,"
                + " then ## Item, ## Status and ## Evidence");
        continue;
      }
      if (!name.startsWith(m.group(2) + "-")) {
        problems.add(
            name
                + ": the name starts with "
                + name.substring(0, Math.max(0, name.indexOf('-')))
                + ", the header says item "
                + m.group(2));
      }
      if (!m.group(1).equals("none")) {
        orders.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(name);
      }
      items.computeIfAbsent(m.group(2), k -> new ArrayList<>()).add(name);
    }
    clashes("item", items, problems);
    clashes("order", orders, problems);
    return problems;
  }

  private static void clashes(String what, Map<String, List<String>> taken, List<String> out) {
    taken.forEach((n, names) -> {
      if (names.size() > 1) out.add(what + " " + n + " is taken by " + String.join(" and ", names));
    });
  }
}
