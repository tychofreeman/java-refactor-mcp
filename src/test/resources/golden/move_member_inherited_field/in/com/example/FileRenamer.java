package com.example;

class FileRenamer extends Renamer {
  FileRenamer() {
    super(System.err);
  }

  private boolean moveFile(String r) {
    err.println(r);
    return true;
  }

  boolean go() {
    return moveFile("x");
  }
}
