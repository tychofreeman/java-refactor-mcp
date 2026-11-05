package com.example;

import java.util.ArrayList;
import java.util.List;

public class Yard {
  final List<String> charged = new ArrayList<>();

  boolean reserve(String truck) {
    return false;
  }

  boolean charge(String truck) {
    charged.add(truck);
    return true;
  }

  public boolean admit(String truck) {
    boolean a = reserve(truck);
    boolean b = charge(truck);
    return a && b;
  }
}
