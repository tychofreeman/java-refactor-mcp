package com.example;

import java.util.List;

class Main {
  record Rename(String from, String to) {}

  static void carryOut(Rename r, boolean rename) {
    System.out.println(r.from() + (rename ? " -> " : " = ") + r.to());
  }

  static void run(List<Rename> renames, boolean rename) {
    renames.stream().filter(r -> !r.from().equals(r.to())).forEach(r -> carryOut(r, rename));
  }
}
