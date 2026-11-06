package lib.loan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** The library's catalog: how many copies it holds of each book, by ISBN. */
public class Library {
  private final Map<String, Integer> copies;

  public Library(Map<String, Integer> copies) {
    this.copies = Map.copyOf(copies);
  }

  /** The catalog in {@code file}: one isbn,copies line per book. */
  public static Library load(Path file) throws IOException {
    Map<String, Integer> copies = new HashMap<>();
    for (String line : Files.readAllLines(file)) {
      if (line.isBlank()) {
        continue;
      }
      String[] parts = line.split(",");
      copies.put(parts[0].strip(), Integer.parseInt(parts[1].strip()));
    }
    return new Library(copies);
  }

  /** How many copies of {@code isbn} the library holds, 0 for one it does not have. */
  public int copies(String isbn) {
    return copies.getOrDefault(isbn, 0);
  }
}
