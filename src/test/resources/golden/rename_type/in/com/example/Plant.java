package com.example;

/** A plant; {@link Plant#Plant(String)} names it. */
public class Plant {
  private final String name;

  public Plant(String name) {
    this.name = name;
  }

  Plant copy() {
    return new Plant(name);
  }
}
