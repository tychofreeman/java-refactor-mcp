package com.example;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class Main {
  void write(List<String> lines, Writer out) throws IOException {
    for (String line : lines) {
      out.write(line);
    }
  }
}
