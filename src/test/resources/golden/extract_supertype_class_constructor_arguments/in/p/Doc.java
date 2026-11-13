package p;

import java.io.IOException;

class Doc {
  protected final String name;

  protected Doc(String name, int pages) throws IOException {
    this.name = name;
  }

  Doc(String name) {
    this.name = name;
  }
}
