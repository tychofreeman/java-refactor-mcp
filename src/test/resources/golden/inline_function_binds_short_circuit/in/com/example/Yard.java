package com.example;

import java.util.ArrayList;
import java.util.List;

public class Yard {
  final List<String> charged = new ArrayList<>();

  static boolean both(boolean a, boolean b) {
    return a && b;
  }

  boolean reserve(String truck) {
    return false;
  }

  boolean charge(String truck) {
    charged.add(truck);
    return true;
  }

  public boolean admit(String truck) {
    return both(reserve(truck), charge(truck));
  }
}
