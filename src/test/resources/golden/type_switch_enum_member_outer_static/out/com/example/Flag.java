package com.example;

public class Flag {
  public enum Location {
    CLASS {
      @Override
      public String[] flags() {
        return CLASS_FLAGS;
      }
    },
    FIELD {
      @Override
      public String[] flags() {
        return FIELD_FLAGS;
      }
    };

    public abstract String[] flags();
  }

  static String[] definition(Location location) {
    return location.flags();
  }

  private static final String[] CLASS_FLAGS = {"public", "final"};
  private static final String[] FIELD_FLAGS = {"volatile"};
}
