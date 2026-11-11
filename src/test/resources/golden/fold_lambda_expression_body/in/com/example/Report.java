package com.example;

import java.io.PrintStream;
import java.util.List;

class Report {
  private final PrintStream err = System.err;

  private void skipped(String skipped) {
    err.println("skipped " + skipped + ": no date");
  }

  void all(List<String> files) {
    files.forEach(skipped -> err.println("skipped " + skipped + ": no date"));
  }
}
