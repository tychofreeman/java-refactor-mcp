package com.example;

class FileRenamer extends Renamer {
  FileRenamer() {
    super(System.err);
  }

  boolean go() {
    return Renamer.moveFile(this, "x");
  }
}
