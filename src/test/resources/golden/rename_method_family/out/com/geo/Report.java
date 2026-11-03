package com.geo;

import java.util.List;
import java.util.function.ToDoubleFunction;

public class Report {
  double total(List<Shape> shapes, Square square) {
    ToDoubleFunction<Shape> f = Shape::surface;
    double sum = square.surface();
    for (Shape s : shapes) {
      sum += f.applyAsDouble(s);
    }
    return sum;
  }
}
