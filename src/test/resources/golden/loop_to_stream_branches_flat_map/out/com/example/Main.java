package com.example;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
  static int count(List<String> paths) {
    return files(paths).size();
  }

  static List<File> files(List<String> paths) {
    List<File> files = paths.stream()
        .map(path -> new File(path))
        .flatMap(file -> file.isDirectory() ? imagesIn(file).stream() : Stream.of(file))
        .collect(Collectors.toList());
    return files;
  }

  static List<File> imagesIn(File directory) {
    return List.of(directory);
  }
}
