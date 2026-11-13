package com.example;

import java.io.File;

public class FileRenamer extends Renamer {
  public FileRenamer(StringBuilder log) {
    super(log);
  }

  int moved;

  @Override
  boolean move(String from, String to) {
    return new File(from).renameTo(new File(to));
  }
}
