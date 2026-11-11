package com.example;

import java.io.PrintStream;

class Renamer {
  final PrintStream err;

  Renamer(PrintStream err) {
    this.err = err;
  }

  static boolean moveFile(FileRenamer receiver, String r) {
    receiver.err.println(r);
    return true;
  }
}
