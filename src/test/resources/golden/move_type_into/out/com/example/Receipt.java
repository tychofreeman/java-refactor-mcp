package com.example;

class Receipt {
  String head(Order.Line line) {
    return line == Order.Line.EMPTY ? "" : line.name;
  }
}
