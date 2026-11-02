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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

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

  private Server() {}

  /** Serves the project at {@code args[0]} (default: the working directory) on stdin and stdout. */
  public static void main(String[] args) throws InterruptedException {
    Path root = Path.of(args.length > 0 ? args[0] : "").toAbsolutePath();
    start(SourceProject.of(root), System.in, System.out);
    new CountDownLatch(1).await();
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

  /** A tool body that returns its text, or refuses. */
  private interface Body {
    String run() throws Exception;
  }

  /**
   * Runs {@code body}: its text, a refusal's reason, or an engine error, which is not a refusal.
   */
  private static CallToolResult answer(Body body) {
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
