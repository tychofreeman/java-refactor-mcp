package com.example;

public enum Color {
  RED {
    @Override
    public String toString() {
      return "red!";
    }

    @Override
    public String label() {
      return name().toLowerCase();
    }
  },
  GREEN {
    @Override
    public String label() {
      return "g".repeat(2);
    }
  };

  public abstract String label();
}
