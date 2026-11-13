package com.example;

class Span {
  final int start;
  final int end;
  final int length;

  Span(int start, int end, int length) {
    this(start, end, length, "");
  }

  Span(int end) {
    this(0, end, end - 0);
  }

  private Span(int start, int end, int length, String label) {
    this.start = start;
    this.end = end;
    this.length = length;
  }
}
