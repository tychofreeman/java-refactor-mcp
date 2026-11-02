package io.github.tychofreeman.jrefactor;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** The MCP server: the project's tools over STDIO. */
public final class Server {

  static final String NAME = "java-refactor-mcp";
  static final String VERSION = "0.1.0";

  private static final String INSPECT_DESCRIPTION =
      """
      Read Java code with line numbers. Use it before a refactoring to get exact lines.
      - file: the file with method, constructor and initializer bodies elided: declarations, \
      fields, signatures and comments in full, and in place of each body of two lines or more \
      the lines it takes.
      - file + start_line/end_line: those lines (end_line defaults to the end of the file).
      Paths are relative to the project root; lines are 1-based.\
      """;

  private static final String INSPECT_SCHEMA =
      """
      {
        "type": "object",
        "properties": {
          "file":       {"type": "string",  "description": "Java file path relative to the project root."},
          "start_line": {"type": "integer", "description": "First line to show (1-based)."},
          "end_line":   {"type": "integer", "description": "Last line to show (inclusive)."}
        },
        "required": ["file"]
      }\
      """;

  // No "additionalProperties": false in the schema: the SDK would refuse an unknown argument
  // before checkArgs, without naming the arguments to use.
  private static final Set<String> INSPECT_ARGS = Set.of("file", "start_line", "end_line");

  private static final String RENAME_LOCAL_DESCRIPTION =
      """
      Rename a local variable or parameter and every use of it in the method that declares it.
      Identify it by file, line (its declaration or any use) and its current name; column only       when the name is on the line twice with different meanings.
      Applies the edit, then type-checks the project: an edit that adds compile errors is       rolled back and refused. Returns the lines changed.
      Refuses, saying why: a field (not built yet), a name that is not a variable, a new name       that is not a valid Java name or that another variable in the method already uses.\
      """;

  private static final String RENAME_LOCAL_SCHEMA =
      """
      {
        "type": "object",
        "properties": {
          "file":     {"type": "string",  "description": "Java file path relative to the project root."},
          "line":     {"type": "integer", "description": "1-based line of the declaration or any use."},
          "name":     {"type": "string",  "description": "The variable's current name."},
          "column":   {"type": "integer", "description": "Optional 1-based column of the name, only to tell two meanings apart."},
          "new_name": {"type": "string",  "description": "The new name; a valid Java name."}
        },
        "required": ["file", "line", "name", "new_name"]
      }\
      """;

  private static final Set<String> RENAME_LOCAL_ARGS =
      Set.of("file", "line", "name", "column", "new_name");

  /** Tool calls running now; the server does not exit at the end of input while one is. */
  private static final AtomicInteger CALLS = new AtomicInteger();

  private static final long QUIET_BEFORE_EXIT_NANOS = 1_000_000_000L;
  private static volatile long lastOutput;

  private Server() {}

  /**
   * Serves the project at {@code args[0]} (default: the working directory) on stdin and stdout, and
   * exits when stdin ends, so a client that goes away leaves no server running.
   */
  public static void main(String[] args) throws InterruptedException {
    Path root = Path.of(args.length > 0 ? args[0] : "").toAbsolutePath();
    SourceProject project = SourceProject.load(root);
    System.err.println(
        "jrefactor: serving "
            + project.root()
            + " with "
            + project.sourceRoots().size()
            + " source root(s) and "
            + project.classpath().size()
            + " classpath entries");
    CountDownLatch inputEnded = new CountDownLatch(1);
    McpSyncServer server =
        start(
            project,
            endingWith(
                System.in,
                () -> {
                  awaitQuiet();
                  inputEnded.countDown();
                }),
            new FilterOutputStream(System.out) {
              @Override
              public void write(int b) throws IOException {
                lastOutput = System.nanoTime();
                out.write(b);
              }

              @Override
              public void write(byte[] b, int off, int len) throws IOException {
                lastOutput = System.nanoTime();
                out.write(b, off, len);
              }
            });
    inputEnded.await();
    server.closeGracefully();
    System.exit(0);
  }

  /**
   * Waits until no tool call is running and nothing has been written for a second. Called from the
   * read that finds the end of input, before it returns: the SDK closes the session when it sees
   * the end, and would drop requests it has read but not yet run.
   */
  private static void awaitQuiet() {
    lastOutput = System.nanoTime();
    try {
      while (CALLS.get() > 0 || System.nanoTime() - lastOutput < QUIET_BEFORE_EXIT_NANOS) {
        Thread.sleep(50);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * {@code in}, calling {@code onEnd} once, when a read finds its end or fails, before it returns.
   */
  static InputStream endingWith(InputStream in, Runnable onEnd) {
    AtomicBoolean ended = new AtomicBoolean();
    return new FilterInputStream(in) {
      @Override
      public int read() throws IOException {
        return check(() -> super.read());
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        return check(() -> super.read(b, off, len));
      }

      private int check(IoRead read) throws IOException {
        try {
          int n = read.run();
          if (n < 0) {
            end();
          }
          return n;
        } catch (IOException | RuntimeException e) {
          end();
          throw e;
        }
      }

      private void end() {
        if (ended.compareAndSet(false, true)) {
          onEnd.run();
        }
      }
    };
  }

  private interface IoRead {
    int run() throws IOException;
  }

  /** Serves {@code project} on {@code in} and {@code out} until the input closes. */
  public static McpSyncServer start(SourceProject project, InputStream in, OutputStream out) {
    McpJsonMapper json = McpJsonDefaults.getMapper();
    Tool inspect =
        Tool.builder("inspect", json, INSPECT_SCHEMA)
            .description(INSPECT_DESCRIPTION)
            .annotations(new ToolAnnotations(null, true, false, true, false, null))
            .build();
    return McpServer.sync(new StdioServerTransportProvider(json, in, out))
        .serverInfo(NAME, VERSION)
        .capabilities(ServerCapabilities.builder().tools(false).build())
        .toolCall(inspect, (exchange, request) -> inspect(project, request.arguments()))
        .toolCall(
            Tool.builder("rename_local", json, RENAME_LOCAL_SCHEMA)
                .description(RENAME_LOCAL_DESCRIPTION)
                .annotations(new ToolAnnotations(null, false, false, false, false, null))
                .build(),
            (exchange, request) -> renameLocal(project, request.arguments()))
        .build();
  }

  private static CallToolResult inspect(SourceProject project, Map<String, Object> args) {
    return answer(() -> {
      checkArgs("inspect", args, INSPECT_ARGS);
      String file = string(args, "file");
      if (file == null) {
        throw new Refusal("inspect needs file: a Java file path relative to the project root.");
      }
      Integer start = integer(args, "start_line");
      Integer end = integer(args, "end_line");
      if (start == null && end == null) {
        return Inspect.file(project, file);
      }
      return Inspect.lines(
          project, file, start == null ? 1 : start, end == null ? Integer.MAX_VALUE : end);
    });
  }

  private static CallToolResult renameLocal(SourceProject project, Map<String, Object> args) {
    return answer(() -> {
      checkArgs("rename_local", args, RENAME_LOCAL_ARGS);
      String file = required(args, "file");
      String name = required(args, "name");
      String newName = required(args, "new_name");
      Integer line = integer(args, "line");
      if (line == null) {
        throw new Refusal("rename_local needs line: the 1-based line of a use or declaration.");
      }
      Integer column = integer(args, "column");
      RenameLocal.Result result =
          RenameLocal.run(project, file, line, name, column == null ? 0 : column, newName);
      StringBuilder lines = new StringBuilder();
      for (int l : result.lines()) {
        lines.append(lines.isEmpty() ? "" : ", ").append(l);
      }
      return "renamed %s to %s in %s: %d occurrences, line%s %s"
          .formatted(
              name,
              newName,
              result.file(),
              result.occurrences(),
              result.lines().size() == 1 ? "" : "s",
              lines);
    });
  }

  private static String required(Map<String, Object> args, String key) {
    String value = string(args, key);
    if (value == null || value.isEmpty()) {
      throw new Refusal(key + " is required.");
    }
    return value;
  }

  /** A tool body that returns its text, or refuses. */
  private interface Body {
    String run() throws Exception;
  }

  /**
   * Runs {@code body}: its text, a refusal's reason, or an engine error, which is not a refusal.
   */
  private static CallToolResult answer(Body body) {
    CALLS.incrementAndGet();
    try {
      return CallToolResult.builder().addTextContent(body.run()).isError(false).build();
    } catch (Refusal refusal) {
      return CallToolResult.builder()
          .addTextContent("refused: " + refusal.getMessage())
          .isError(true)
          .build();
    } catch (Exception e) {
      return CallToolResult.builder()
          .addTextContent("engine error (not a refusal; the call may be fine): " + e)
          .isError(true)
          .build();
    } finally {
      CALLS.decrementAndGet();
    }
  }

  private static void checkArgs(String tool, Map<String, Object> args, Set<String> known) {
    Set<String> unknown = new TreeSet<>(args.keySet());
    unknown.removeAll(known);
    if (!unknown.isEmpty()) {
      throw new Refusal(
          tool
              + " does not take "
              + String.join(", ", unknown)
              + ". Its arguments are "
              + String.join(", ", new TreeSet<>(known))
              + ".");
    }
  }

  private static String string(Map<String, Object> args, String key) {
    Object value = args.get(key);
    if (value == null || value instanceof String) {
      return (String) value;
    }
    throw new Refusal(key + " must be a string; got " + value + ".");
  }

  private static Integer integer(Map<String, Object> args, String key) {
    Object value = args.get(key);
    if (value == null) {
      return null;
    }
    if (value instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
      return n.intValue();
    }
    throw new Refusal(key + " must be a whole number; got " + value + ".");
  }
}
