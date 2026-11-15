package com.example;

class Receipt {
  String head(Shop.Line line) {
    return line == Shop.Line.EMPTY ? "" : line.name;
  }
}
