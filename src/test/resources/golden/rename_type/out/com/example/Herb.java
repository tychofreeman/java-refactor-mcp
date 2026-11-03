package com.example;

/** A plant; {@link Herb#Herb(String)} names it. */
public class Herb {
  private final String name;

  public Herb(String name) {
    this.name = name;
  }

  Herb copy() {
    return new Herb(name);
  }
}
