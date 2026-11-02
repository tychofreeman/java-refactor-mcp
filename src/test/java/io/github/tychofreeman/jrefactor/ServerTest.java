package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpSyncServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** Drives the server over in-process pipes, as Claude Code would over stdin and stdout. */
@Timeout(30)
class ServerTest {

  /** Golden inputs copied into one project per test, so a rename cannot change them. */
  private static final List<Path> INPUTS =
      List.of(
          Path.of("src/test/resources/golden/inspect_outline/in"),
          Path.of("src/test/resources/golden/rename_local_basic/in"));

  @TempDir Path project;

  private final McpJsonMapper json = McpJsonDefaults.getMapper();
  private OutputStream toServer;
  private BufferedReader fromServer;
  private McpSyncServer server;

  @BeforeEach
  void start() throws IOException {
    for (Path input : INPUTS) {
      try (Stream<Path> files = Files.walk(input)) {
        for (Path src : files.filter(Files::isRegularFile).toList()) {
          Path dst = project.resolve(input.relativize(src).toString());
          Files.createDirectories(dst.getParent());
          Files.copy(src, dst);
        }
      }
    }
    PipedOutputStream clientOut = new PipedOutputStream();
    PipedInputStream serverIn = new PipedInputStream(clientOut, 1 << 16);
    PipedOutputStream serverOut = new PipedOutputStream();
    PipedInputStream clientIn = new PipedInputStream(serverOut, 1 << 16);
    toServer = clientOut;
    fromServer = new BufferedReader(new InputStreamReader(clientIn, StandardCharsets.UTF_8));
    server = Server.start(SourceProject.of(project), serverIn, serverOut);

    Map<String, Object> init =
        request(
            1,
            "initialize",
            Map.of(
                "protocolVersion", "2025-06-18",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "test", "version", "0")));
    assertEquals("java-refactor-mcp", ((Map<?, ?>) result(init).get("serverInfo")).get("name"));
    send(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
  }

  @AfterEach
  void stop() {
    server.close();
  }

  @Test
  void listsInspectAsReadOnly() throws IOException {
    List<?> tools = (List<?>) result(request(2, "tools/list", Map.of())).get("tools");
    Map<?, ?> inspect =
        tools.stream()
            .map(t -> (Map<?, ?>) t)
            .filter(t -> "inspect".equals(t.get("name")))
            .findFirst()
            .orElseThrow();
    assertEquals(true, ((Map<?, ?>) inspect.get("annotations")).get("readOnlyHint"));
  }

  @Test
  void inspectReturnsLines() throws IOException {
    Map<String, Object> result =
        result(call(3, Map.of("file", "com/example/Shapes.java", "start_line", 1, "end_line", 1)));
    assertEquals(false, result.get("isError"));
    assertEquals(
        "com/example/Shapes.java  lines 1-1 of 44:\n 1  package com.example;\n", text(result));
  }

  @Test
  void refusalIsAnErrorResultWithItsReason() throws IOException {
    Map<String, Object> result = result(call(4, Map.of("file", "com/example/Shape.java")));
    assertEquals(true, result.get("isError"));
    assertTrue(text(result).contains("No Java file com/example/Shape.java"), text(result));
  }

  @Test
  void unknownArgumentIsRefusedWithTheArgumentsToUse() throws IOException {
    Map<String, Object> result =
        result(call(5, Map.of("file", "com/example/Shapes.java", "line", 3)));
    assertEquals(true, result.get("isError"));
    assertTrue(
        text(result).contains("inspect does not take line. Its arguments are end_line, file,"),
        text(result));
  }

  @Test
  void renameLocalAppliesAndListsTheLines() throws IOException {
    Map<String, Object> result =
        result(
            call(
                6,
                "rename_local",
                Map.of(
                    "file", "com/example/Totals.java",
                    "line", 5,
                    "name", "total",
                    "new_name", "runningSum")));
    assertEquals(false, result.get("isError"), () -> text(result));
    assertEquals(
        "renamed total to runningSum in com/example/Totals.java: 3 occurrences, lines 5, 7, 10",
        text(result));
    assertTrue(
        Files.readString(project.resolve("com/example/Totals.java")).contains("runningSum += v;"));
  }

  @Test
  void renameLocalRefusalChangesNothing() throws IOException {
    String before = Files.readString(project.resolve("com/example/Totals.java"));
    Map<String, Object> result =
        result(
            call(
                7,
                "rename_local",
                Map.of(
                    "file", "com/example/Totals.java",
                    "line", 5,
                    "name", "total",
                    "new_name", "label")));
    assertEquals(true, result.get("isError"));
    assertTrue(text(result).startsWith("refused: "), text(result));
    assertTrue(text(result).contains("already a variable"), text(result));
    assertEquals(before, Files.readString(project.resolve("com/example/Totals.java")));
  }

  private Map<String, Object> call(int id, Map<String, Object> arguments) throws IOException {
    return call(id, "inspect", arguments);
  }

  private Map<String, Object> call(int id, String tool, Map<String, Object> arguments)
      throws IOException {
    return request(id, "tools/call", Map.of("name", tool, "arguments", arguments));
  }

  private Map<String, Object> request(int id, String method, Map<String, Object> params)
      throws IOException {
    send(Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
    while (true) {
      String line = fromServer.readLine();
      if (line == null) {
        throw new IOException("server closed its output");
      }
      Map<String, Object> message = asMap(json.readValue(line, Map.class));
      if (Integer.valueOf(id).equals(message.get("id"))) {
        return message;
      }
    }
  }

  private void send(Map<String, Object> message) throws IOException {
    toServer.write((json.writeValueAsString(message) + "\n").getBytes(StandardCharsets.UTF_8));
    toServer.flush();
  }

  private static Map<String, Object> result(Map<String, Object> response) {
    assertTrue(response.containsKey("result"), () -> "expected a result, got " + response);
    return asMap(response.get("result"));
  }

  private static String text(Map<String, Object> result) {
    return (String) ((Map<?, ?>) ((List<?>) result.get("content")).get(0)).get("text");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }
}
