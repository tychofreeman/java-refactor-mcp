package com.geo;

import java.util.List;

public class Shapes {
  static class Square {
    double area(List<Double> sides) {
      return sides.get(0) * sides.get(0);
    }

    double scaled(List<Double> factors) {
      return factors.get(0);
    }
  }
}
