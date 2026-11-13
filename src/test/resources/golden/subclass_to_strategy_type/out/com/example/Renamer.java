package com.example;

import java.io.File;
import java.util.function.BiPredicate;

public class Renamer {
  private final StringBuilder log;
  private final BiPredicate<String, String> move;

  public Renamer(StringBuilder log, BiPredicate<String, String> move) {
    this.move = move;
    this.log = log;
  }

  public Renamer(StringBuilder log) {
    this(log, Renamer::defaultMove);
  }

  boolean move(String from, String to) {
    return move.test(from, to);
  }

  private static boolean defaultMove(String from, String to) {
    return true;
  }

  public static boolean moveFile(String from, String to) {
    return new File(from).renameTo(new File(to));
  }

  void run(String from, String to) {
    if (move(from, to)) {
      log.append(from).append(" -> ").append(to);
    }
  }
}
