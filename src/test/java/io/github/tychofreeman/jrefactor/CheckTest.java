package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckTest {

  private static final String CART = "src/main/java/com/example/Cart.java";

  private static SourceProject project(Path root) throws Exception {
    SourceProject project = TestProjects.maven(root, false);
    TestProjects.write(root, "pom.xml", "<project/>\n");
    TestProjects.write(root, CART, "package com.example;\n\nclass Cart {}\n");
    TestProjects.write(
        root,
        "src/test/java/com/example/CartTest.java",
        "package com.example;\n\nclass CartTest {\n  Cart cart = new Cart();\n}\n");
    TestProjects.write(
        root,
        "src/test/java/com/example/OtherTest.java",
        "package com.example;\n\nclass OtherTest {}\n");
    return project;
  }

  private static Map<String, Object> addTotal() {
    return Map.of(
        "tool",
        "add_declaration",
        "args",
        Map.of("file", CART, "content", "int total() {\n  return 1;\n}\n"));
  }

  @Test
  void theTestsFocusedOnAChangeAreThoseChangedAndThoseUsingAChangedType(@TempDir Path root)
      throws Exception {
    SourceProject project = project(root);
    TestProjects.write(
        root,
        "src/test/java/com/example/NewTest.java",
        "package com.example;\n\nclass NewTest {}\n");
    assertEquals(
        List.of("com.example.CartTest", "com.example.NewTest"),
        Check.focused(
            project,
            List.of(root.resolve(CART), root.resolve("src/test/java/com/example/NewTest.java"))));
  }

  @Test
  void aReportNamesEachFailureByTestAndLine() throws Exception {
    Check.Run run =
        Check.report(
            """
            <testsuite name="com.example.CartTest" tests="2">
              <testcase name="adds" classname="com.example.CartTest"/>
              <testcase name="totals" classname="com.example.CartTest">
                <failure message="expected: &lt;3&gt; but was: &lt;2&gt;" type="AssertionFailedError">
            org.opentest4j.AssertionFailedError: expected: &lt;3&gt; but was: &lt;2&gt;
            \tat org.junit.jupiter.api.AssertEquals.failNotEqual(AssertEquals.java:197)
            \tat com.example.CartTest.totals(CartTest.java:12)
                </failure>
              </testcase>
            </testsuite>
            """);
    assertEquals(2, run.tests());
    assertEquals(
        List.of("com.example.CartTest.totals (CartTest.java:12): expected: <3> but was: <2>"),
        run.failures().stream().map(Check.Failure::text).toList());
  }

  @Test
  void checkRunsTheTestsThisSessionsChangesReach(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    List<List<String>> ran = new ArrayList<>();
    project.session().tests =
        (at, classes, seconds) -> {
          ran.add(classes);
          return new Check.Run(1, List.of(), null);
        };
    Tools.call(Tools.named("batch"), project, Map.of("steps", List.of(addTotal())));

    String said = Tools.call(Tools.named("check"), project, Map.of("run_tests", true)).render();

    assertEquals(List.of(List.of("com.example.CartTest")), ran);
    assertTrue(said.startsWith("type-checks: no compile errors"), said);
    assertTrue(
        said.contains("1 test of CartTest (those that use this session's changes) passed"), said);
  }

  @Test
  void aCheckpointWhoseTestsFailIsRefused(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    project.session().tests =
        (at, classes, seconds) ->
            new Check.Run(
                1,
                List.of(
                    new Check.Failure("com.example.CartTest.totals", "CartTest.java:4", "boom")),
                null);
    Refusal refused =
        assertThrows(
            Refusal.class,
            () ->
                Tools.call(
                    Tools.named("batch"),
                    project,
                    Map.of(
                        "steps",
                        List.of(
                            addTotal(),
                            Map.of("tool", "check", "args", Map.of("run_tests", true))))));
    assertTrue(
        refused.getMessage().contains("com.example.CartTest.totals (CartTest.java:4): boom"),
        refused.getMessage());
    assertEquals("package com.example;\n\nclass Cart {}\n", Files.readString(root.resolve(CART)));
  }

  @Test
  void aTestIsFocusedWhenItsCodeUsesATypeOfAChangedFile(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    TestProjects.write(
        root, CART, "package com.example;\n\nclass Cart {\n  record Line(int n) {}\n}\n");
    TestProjects.write(
        root,
        "src/test/java/com/example/LineTest.java",
        "package com.example;\n\nclass LineTest {\n  Object line = new Cart.Line(1);\n}\n");
    TestProjects.write(
        root,
        "src/test/java/com/example/NoteTest.java",
        "package com.example;\n\n/** Not about Cart. */\nclass NoteTest {}\n");
    assertEquals(
        List.of("com.example.CartTest", "com.example.LineTest"),
        Check.focused(project, List.of(root.resolve(CART))));
  }

  @Test
  void testsNamesTheTestsToRunAndTimeoutHowLongTheyMayTake(@TempDir Path root) throws Exception {
    SourceProject project = project(root);
    List<String> ran = new ArrayList<>();
    project.session().tests =
        (at, classes, seconds) -> {
          ran.addAll(classes);
          ran.add(seconds + " s");
          return new Check.Run(1, List.of(), null);
        };
    Tools.call(
        Tools.named("check"),
        project,
        Map.of("tests", List.of("OtherTest#nothing"), "timeout", 90));
    Tools.call(Tools.named("check"), project, Map.of("tests", List.of("OtherTest")));
    assertEquals(List.of("OtherTest#nothing", "90 s", "OtherTest", "30 s"), ran);
  }
}
