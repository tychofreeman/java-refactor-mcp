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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** The MCP server: the project's tools over STDIO. */
public final class Server {

  static final String NAME = "java-refactor-mcp";
  static final String VERSION = "0.1.0";

  private static final long QUIET_BEFORE_EXIT_NANOS = 1_000_000_000L;

  /** What one running server knows of its calls: how many run, and when it last wrote. */
  static final class Quiet {
    /**
     * Held while a tool runs. The SDK can run calls sent together at the same time; two edits of
     * one file would each read it before the other wrote, and the second would undo the first.
     */
    final Object oneAtATime = new Object();

    /** Tool calls running now; the server does not exit at the end of input while one is. */
    final AtomicInteger calls = new AtomicInteger();

    volatile long lastOutput;
  }

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
    Quiet quiet = new Quiet();
    McpSyncServer server =
        start(
            project,
            endingWith(
                System.in,
                () -> {
                  awaitQuiet(quiet);
                  inputEnded.countDown();
                }),
            new FilterOutputStream(System.out) {
              @Override
              public void write(int b) throws IOException {
                quiet.lastOutput = System.nanoTime();
                out.write(b);
              }

              @Override
              public void write(byte[] b, int off, int len) throws IOException {
                quiet.lastOutput = System.nanoTime();
                out.write(b, off, len);
              }
            },
            quiet);
    inputEnded.await();
    server.closeGracefully();
    System.exit(0);
  }

  /**
   * Waits until no tool call is running and nothing has been written for a second. Called from the
   * read that finds the end of input, before it returns: the SDK closes the session when it sees
   * the end, and would drop requests it has read but not yet run.
   */
  private static void awaitQuiet(Quiet quiet) {
    quiet.lastOutput = System.nanoTime();
    try {
      while (quiet.calls.get() > 0
          || System.nanoTime() - quiet.lastOutput < QUIET_BEFORE_EXIT_NANOS) {
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
    return start(project, in, out, new Quiet());
  }

  private static McpSyncServer start(
      SourceProject project, InputStream in, OutputStream out, Quiet quiet) {
    McpJsonMapper json = McpJsonDefaults.getMapper();
    var spec =
        McpServer.sync(new StdioServerTransportProvider(json, in, out))
            .serverInfo(NAME, VERSION)
            .capabilities(ServerCapabilities.builder().tools(false).build());
    for (Tools.Def def : Tools.ALL) {
      spec.toolCall(
          Tool.builder(def.name(), json, def.schema())
              .description(def.description())
              .annotations(
                  new ToolAnnotations(null, def.readOnly(), false, def.readOnly(), false, null))
              .build(),
          (exchange, request) ->
              answer(quiet, () -> Tools.call(def, project, request.arguments()).render()));
    }
    return spec.build();
  }

  /** A tool body that returns its text, or refuses. */
  interface Body {
    String run() throws Exception;
  }

  /**
   * Runs {@code body}: its text, a refusal's reason, or an engine error, which is not a refusal.
   */
  static CallToolResult answer(Quiet quiet, Body body) {
    quiet.calls.incrementAndGet();
    try {
      String text;
      synchronized (quiet.oneAtATime) {
        text = body.run();
      }
      return CallToolResult.builder().addTextContent(text).isError(false).build();
    } catch (Refusal refusal) {
      return CallToolResult.builder()
          .addTextContent(Refusal.PREFIX + refusal.getMessage())
          .isError(true)
          .build();
    } catch (Exception e) {
      e.printStackTrace(System.err);
      String where =
          java.util.Arrays.stream(e.getStackTrace())
              .limit(3)
              .map(f -> "\n  at " + f)
              .collect(java.util.stream.Collectors.joining());
      return CallToolResult.builder()
          .addTextContent("engine error (not a refusal; the call may be fine): " + e + where)
          .isError(true)
          .build();
    } finally {
      quiet.calls.decrementAndGet();
    }
  }
}
