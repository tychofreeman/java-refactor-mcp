package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuardTest {

  private static String call(String tool, String key, String value) {
    return SessionLog.JSON
        .createObjectNode()
        .put("tool_name", tool)
        .put("cwd", "/home/u/p")
        .set("tool_input", SessionLog.JSON.createObjectNode().put(key, value))
        .toString();
  }

  private static String read(String path) {
    return call("Read", "file_path", path);
  }

  private static String bash(String command) {
    return call("Bash", "command", command);
  }

  private static String edit(String tool, String path) {
    return call(tool, "file_path", path);
  }

  private static final String MAIN = "src/main/java/com/example/Plant.java";

  @Test
  void readGuardRefusesReadingJavaOtherThanThroughInspect() {
    for (String c :
        List.of(
            read("/home/u/p/" + MAIN),
            read("Plant.java"),
            bash("sed -n 1,20p " + MAIN),
            bash("cd src/main/java && cat com/example/Plant.java | head -5"),
            bash("grep -n water " + MAIN),
            bash("cat < Plant.java"),
            bash("LC_ALL=C tail -3 Plant.java"))) {
      assertNotEquals("", Guard.refusal(false, c), c);
    }
    for (String c :
        List.of(
            read("docs/design.md"),
            read("src/test/resources/golden/rename_local/in/com/example/A.java"),
            bash("grep -rn water src"),
            bash("git diff " + MAIN),
            bash("./mvnw -q test"),
            bash("grep -c water docs > count.java"),
            bash("echo \"cat Plant.java\""))) {
      assertEquals("", Guard.refusal(false, c), c);
    }
  }

  @Test
  void writeGuardRefusesChangingJavaOtherThanThroughTheTools() {
    for (String c :
        List.of(
            edit("Edit", "/home/u/p/" + MAIN),
            edit("Write", "Plant.java"),
            edit("MultiEdit", MAIN),
            bash("sed -i 's/water/soak/' " + MAIN),
            bash("perl -pi -e 's/a/b/' Plant.java"),
            bash("echo 'class Plant {}' > Plant.java"),
            bash("cat head.txt >> Plant.java"),
            bash("printf x | tee Plant.java"),
            bash("cp /tmp/Plant.java " + MAIN),
            bash("mv Plant.java Herb.java"),
            bash("rm " + MAIN),
            bash("cd src/main/java/com/example && echo x > Plant.java"))) {
      assertNotEquals("", Guard.refusal(true, c), c);
    }
    for (String c :
        List.of(
            edit("Edit", "docs/design.md"),
            edit("Write", "src/test/resources/golden/rename_local/out/com/example/A.java"),
            bash("./mvnw -q test > /tmp/out.txt 2>&1"),
            bash("./mvnw spotless:apply"),
            bash("sed 's/a/b/' Plant.java > /tmp/plant.txt"),
            bash("cp " + MAIN + " /tmp/Plant.java.orig"),
            bash("echo \"x > Plant.java\""),
            bash("git checkout main"))) {
      assertEquals("", Guard.refusal(true, c), c);
    }
  }
}
