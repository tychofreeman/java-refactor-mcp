package com.example;

import java.io.PrintStream;

public class Renamer {
  interface Move {
    boolean apply(String from, PrintStream err);
  }

  static final Move DRY_RUN = (from, err) -> true;
  static final Move CHECKED = (from, err) -> err.checkError();
  static final Move LOGGED =
      (from, err) -> {
        err.println(from);
        return true;
      };
  static final Move REAL = Renamer::moveFile;

  static boolean moveFile(String from, PrintStream err) {
    if (from.isEmpty()) {
      return err.checkError();
    }
    if (from.equals(".")) return false;
    err.println(from);
    return true;
  }

  void run(Move move, String from, PrintStream err) throws java.io.IOException {
    move.apply(from, err);
  }
}
