package com.example;

import java.io.IOException;
import java.io.PrintStream;

public class Renamer {
  interface Move {
    void apply(String from, PrintStream err) throws IOException;
  }

  static final Move DRY_RUN = (from, err) -> {};
  static final Move CHECKED = (from, err) -> err.checkError();
  static final Move LOGGED =
      (from, err) -> {
        err.println(from);
      };
  static final Move REAL = Renamer::moveFile;

  static void moveFile(String from, PrintStream err) throws IOException {
    if (from.isEmpty()) {
      err.checkError();
      return;
    }
    if (from.equals(".")) return;
    err.println(from);
  }

  void run(Move move, String from, PrintStream err) throws java.io.IOException {
    move.apply(from, err);
  }
}
