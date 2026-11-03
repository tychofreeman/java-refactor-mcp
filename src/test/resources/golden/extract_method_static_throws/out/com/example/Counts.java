package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class Counts {
  static int count(Path path) throws IOException {
    int lines = countLines(path);
    return lines + 1;
  }

  private static int countLines(Path path) throws IOException {
    String text = Files.readString(path);
    int lines = text.split("\n").length;
    return lines;
  }
}
