package com.example;

import java.util.function.BiPredicate;

public class FileRenamer extends Renamer {
  public FileRenamer(StringBuilder log) {
    super(log);
  }

  @Override
  boolean move(String from, String to) {
    BiPredicate<String, String> dry = super::move;
    return dry.test(from, to) && !from.equals(to);
  }
}
