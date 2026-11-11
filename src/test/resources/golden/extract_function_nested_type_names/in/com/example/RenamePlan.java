package com.example;

import java.util.ArrayList;
import java.util.List;

record RenamePlan(List<RenamePlan.Rename> renames) {
  record Rename(String from) {}

  record Skipped(String why) {}

  static RenamePlan of(List<String> files) {
    List<Rename> renames = new ArrayList<>();
    List<Skipped> skipped = new ArrayList<>();
    for (String f : files) {
      if (f.isEmpty()) {
        skipped.add(new Skipped(f));
      } else {
        renames.add(new Rename(f));
      }
    }
    System.out.println(skipped);
    return new RenamePlan(renames);
  }
}
