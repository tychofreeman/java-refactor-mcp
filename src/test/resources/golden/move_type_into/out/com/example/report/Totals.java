package com.example.report;

import com.example.Order;
import com.example.Shop.*;

public class Totals {
  int count(Order.Line line) {
    return line.name.length();
  }
}
