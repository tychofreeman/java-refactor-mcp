package com.example;

import java.util.ArrayList;
import java.util.List;

public class Names {
  static List<String> kept(List<String> in) {
    List<String> out = new ArrayList<>();
    for (String s : in) {
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return out;
  }
}

class Report {
  String write(List<String> names) {
    List<String> list = new ArrayList<>();
    for (String n : names) {
      if (!n.isEmpty()) {
        list.add(n);
      }
    }
    list.sort(null);
    return String.join(",", list);
  }

  List<String> same(List<String> names) {
    List<String> list = new ArrayList<>();
    for (String n : names) {
      if (!n.isEmpty()) {
        list.add(n);
      }
    }
    return list;
  }
}
