package com.example;

import java.util.ArrayList;
import java.util.List;

/** A list of shapes and their total area. */
public class Shapes {
  private final List<Double> areas = new ArrayList<>();

  static {
    System.out.println("loaded");
  }

  public Shapes() {
    areas.add(0.0);
  }

  /** Adds a circle of the given radius. */
  public void addCircle(double radius) {
    double area = Math.PI * radius * radius;
    areas.add(area);
  }

  public double total() {
    double sum = 0;
    for (double a : areas) {
      sum += a;
    }
    return sum;
  }

  public int size() { return areas.size(); }

  interface Shape {
    double area();
  }

  record Square(double side) implements Shape {
    public double area() {
      double a = side * side;
      return a;
    }
  }
}
