package com.example;

public class Flag {
  public enum Location {
    CLASS,
    FIELD
  }

  static String[] definition(Location location) {
    return switch (location) {
      case CLASS -> CLASS_FLAGS;
      case FIELD -> FIELD_FLAGS;
    };
  }

  private static final String[] CLASS_FLAGS = {"public", "final"};
  private static final String[] FIELD_FLAGS = {"volatile"};
}
