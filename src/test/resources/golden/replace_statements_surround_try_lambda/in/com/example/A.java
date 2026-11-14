package com.example;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

class A {
  List<Callable<String>> readers(Path p) {
    List<Callable<String>> jobs = new ArrayList<>();
    jobs.add(() -> Files.readString(p));
    return jobs;
  }
}
