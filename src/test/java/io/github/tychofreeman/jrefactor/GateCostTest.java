package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.modelcontextprotocol.json.McpJsonDefaults;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Times the gate on a large project (roadmap #10). Opt-in: runs only with {@code
 * -Djrefactor.cost.root=<project root>}, for example a sources jar unpacked into a directory.
 * Prints the times; asserts only that an edit that adds a comment adds no errors.
 */
class GateCostTest {

  @Test
  @EnabledIfSystemProperty(named = "jrefactor.cost.root", matches = ".+")
  void timesTheGate() throws Exception {
    SourceProject project = SourceProject.load(Path.of(System.getProperty("jrefactor.cost.root")));
    List<Path> files = project.javaFiles();

    long start = System.nanoTime();
    Map<String, Integer> errors = TypeCheck.errors(project);
    long cold = System.nanoTime();
    TypeCheck.errors(project);
    long warm = System.nanoTime();

    Path file = files.get(0);
    String text = Files.readString(file);
    List<String> introduced;
    try {
      introduced = Gate.apply(project, Map.of(file, text + "\n// gate cost\n")).introduced();
    } finally {
      Files.writeString(file, text);
    }
    long edit = System.nanoTime();

    System.out.printf(
        "gate cost: %d files, %d errors; errors() %d ms cold, %d ms warm;"
            + " one edit through Gate.apply %d ms%n",
        files.size(),
        errors.values().stream().mapToInt(Integer::intValue).sum(),
        (cold - start) / 1_000_000,
        (warm - cold) / 1_000_000,
        (edit - warm) / 1_000_000);
    assertEquals(List.of(), introduced);
  }

  /**
   * Times one tool call and its undo on the project: {@code -Djrefactor.cost.call='{"tool":
   * "rename", "args": {...}}'}. Undo restores the project.
   */
  @Test
  @EnabledIfSystemProperty(named = "jrefactor.cost.call", matches = ".+")
  void timesACall() throws Exception {
    SourceProject project = SourceProject.load(Path.of(System.getProperty("jrefactor.cost.root")));
    Map<?, ?> call =
        McpJsonDefaults.getMapper().readValue(System.getProperty("jrefactor.cost.call"), Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> args = (Map<String, Object>) call.get("args");
    TypeCheck.errors(project);

    int parses = TypeCheck.parses(project);
    int bindingParses = project.session().bindingParses;
    long start = System.nanoTime();
    String result = Tools.call(Tools.named((String) call.get("tool")), project, args).render();
    long done = System.nanoTime();
    int parsed = TypeCheck.parses(project) - parses;
    int bound = project.session().bindingParses - bindingParses;
    int files = TypeCheck.lastParsed(project).size();
    // The narrowed count after the call, and after its undo, are what a fresh count gives.
    assertEquals(TypeCheck.uncached(project), TypeCheck.errors(project), "after the call");
    long undoing = System.nanoTime();
    Journal.undo(project);
    long undone = System.nanoTime();

    System.out.printf(
        "call cost: %s %d ms, %d project parses, the last of %d files, %d binding parses in all,"
            + " undo %d ms; %s%n",
        call.get("tool"),
        (done - start) / 1_000_000,
        parsed,
        files,
        bound,
        (undone - undoing) / 1_000_000,
        result.length() > 200 ? result.substring(0, 200) + "..." : result);
    assertEquals(TypeCheck.uncached(project), TypeCheck.errors(project), "after the undo");
  }
}
