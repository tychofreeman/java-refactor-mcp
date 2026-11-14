package com.example;

public class App {
  Renamer dry(StringBuilder log) {
    return new Renamer(log);
  }

  FileRenamer real(StringBuilder log) {
    FileRenamer r = new FileRenamer(log);
    return r;
  }
}
