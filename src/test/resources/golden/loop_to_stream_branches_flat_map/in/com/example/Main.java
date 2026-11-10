package com.example;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class Main {
  static int count(List<String> paths) {
    return files(paths).size();
  }

  static List<File> files(List<String> paths) {
    List<File> files = new ArrayList<>();
    for (String path : paths) {
      File file = new File(path);
      if (file.isDirectory()) {
        files.addAll(imagesIn(file));
      } else {
        files.add(file);
      }
    }
    return files;
  }

  static List<File> imagesIn(File directory) {
    return List.of(directory);
  }
}
