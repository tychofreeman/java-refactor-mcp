package com.example;

public class App {
  Renamer dry(StringBuilder log) {
    return new Renamer(log);
  }

  Renamer real(StringBuilder log) {
    Renamer r = new Renamer(log, Renamer::moveFile);
    return r;
  }
}
