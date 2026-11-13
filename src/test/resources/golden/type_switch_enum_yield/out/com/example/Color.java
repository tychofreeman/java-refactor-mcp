package com.example;

public enum Color {
  RED {
    @Override
    public String label() {
      String s = name();
      if (s.isEmpty()) {
        return "none";
      }
      return s.toLowerCase();
    }
  },
  GREEN {
    @Override
    public String label() {
      return "cool";
    }
  },
  BLUE {
    @Override
    public String label() {
      return "cool";
    }
  };

  public abstract String label();
}
