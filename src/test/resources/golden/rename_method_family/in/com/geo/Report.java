package com.geo;

import java.util.List;
import java.util.function.ToDoubleFunction;

public class Report {
  double total(List<Shape> shapes, Square square) {
    ToDoubleFunction<Shape> f = Shape::area;
    double sum = square.area();
    for (Shape s : shapes) {
      sum += f.applyAsDouble(s);
    }
    return sum;
  }
}
