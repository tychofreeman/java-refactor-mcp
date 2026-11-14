package com.example;

import java.util.List;

public class Renamer {
  public interface Move {
    boolean move(String from, String to);
  }

  private final StringBuilder log;
  private final Move move;

  public Renamer(StringBuilder log, Move move) {
    this.move = move;
    this.log = log;
  }

  public Renamer(StringBuilder log) {
    this(log, Renamer::defaultMove);
  }

  boolean move(String from, String to) {
    return move.move(from, to);
  }

  private static boolean defaultMove(String from, String to) {
    return true;
  }

  public static boolean moveFile(String from, String to) {
    return List.of(from, to).stream().map(String::trim).noneMatch(String::isEmpty);
  }

  void run(String from, String to) {
    if (move(from, to)) {
      log.append(from).append(" -> ").append(to);
    }
  }
}
