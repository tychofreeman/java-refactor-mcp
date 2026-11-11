package com.example;

import java.util.ArrayList;
import java.util.List;

record RenamePlan(List<RenamePlan.Rename> renames) {
  record Rename(String from) {}

  record Skipped(String why) {}

  static RenamePlan of(List<String> files) {
    SplitResult splitResult = split(files);
    List<Rename> renames = splitResult.renames();
    List<Skipped> skipped = splitResult.skipped();
    System.out.println(skipped);
    return new RenamePlan(renames);
  }

  private static SplitResult split(List<String> files) {
    List<Rename> renames = new ArrayList<>();
    List<Skipped> skipped = new ArrayList<>();
    for (String f : files) {
      if (f.isEmpty()) {
        skipped.add(new Skipped(f));
      } else {
        renames.add(new Rename(f));
      }
    }
    return new SplitResult(renames, skipped);
  }

  private record SplitResult(List<Rename> renames, List<Skipped> skipped) {}
}
