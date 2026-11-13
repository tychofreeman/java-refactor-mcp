package p;

import java.io.IOException;

public class Titled extends Doc {
  protected Titled(String name, int pages) throws IOException {
    super(name, pages);
  }

  Titled(String name) {
    super(name);
  }

  String title() {
    return name.toUpperCase();
  }
}
