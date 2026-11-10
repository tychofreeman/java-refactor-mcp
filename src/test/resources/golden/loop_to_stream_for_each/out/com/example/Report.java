package com.example;

import java.util.List;

public class Report {
  void print(List<String> names) {
    names.forEach(name -> System.out.println(name));
  }
}
