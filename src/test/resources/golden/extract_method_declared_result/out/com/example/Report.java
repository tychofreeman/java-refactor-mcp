package com.example;

import java.util.List;

public class Report {
  private final StringBuilder out = new StringBuilder();

  public String print(List<Integer> values) {
    int total = sum(values);
    out.append("total: ").append(total);
    return out.toString();
  }

  private int sum(List<Integer> values) {
    int total = 0;
    for (int v : values) {
      total += v;
    }
    return total;
  }
}
