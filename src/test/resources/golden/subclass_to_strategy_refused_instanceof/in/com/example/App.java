package com.example;

public class App {
  Renamer dry(StringBuilder log) {
    return new Renamer(log);
  }

  FileRenamer real(StringBuilder log) {
    FileRenamer r = new FileRenamer(log);
    if (r instanceof FileRenamer) {
      return r;
    }
    return r;
  }
}
