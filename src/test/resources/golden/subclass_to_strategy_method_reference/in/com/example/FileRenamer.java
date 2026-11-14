package com.example;

import java.util.List;

public class FileRenamer extends Renamer {
  public FileRenamer(StringBuilder log) {
    super(log);
  }

  @Override
  boolean move(String from, String to) {
    return List.of(from, to).stream().map(String::trim).noneMatch(String::isEmpty);
  }
}
