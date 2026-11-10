package com.example;

import java.util.List;
import java.util.function.Function;

class Main {
  record Rename(String from, String to) {}

  static boolean carryOut(Rename r, boolean rename) {
    return rename && !r.from().equals(r.to());
  }

  static boolean run(List<Rename> renames, boolean rename) {
    Function<Main.Rename, Boolean> carry = r -> carryOut(r, rename);
    boolean allDone =
        renames.stream().map(carry).reduce(true, Boolean::logicalAnd);
    return allDone;
  }
}
