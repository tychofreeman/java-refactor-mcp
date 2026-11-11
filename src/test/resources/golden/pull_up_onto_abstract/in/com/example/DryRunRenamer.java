package com.example;

public class DryRunRenamer extends Renamer {
  @Override
  boolean carryOut(String rename) {
    System.out.println("would " + rename);
    return true;
  }

  String name() {
    return "dry run";
  }
}
