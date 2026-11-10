package com.example;

import java.util.List;

class Main {
  record Rename(String from, String to) {}

  static boolean carryOut(Rename r, boolean rename) {
    return rename && !r.from().equals(r.to());
  }

  static boolean run(List<Rename> renames, boolean rename) {
    boolean allDone =
        renames.stream().map(r -> carryOut(r, rename)).reduce(true, Boolean::logicalAnd);
    return allDone;
  }
}
