package warehouse.stock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** The moves to make, as words: from-bin, to-bin, quantity, and again. */
public final class PickList {

  private final Deque<String> words;

  private PickList(Deque<String> words) {
    this.words = words;
  }

  /** Splits text into the words of a pick list. */
  public static PickList parse(String text) {
    return new PickList(new ArrayDeque<>(List.of(text.trim().split("\\s+"))));
  }

  /** Whether a whole move is left to read. */
  boolean more() {
    return words.size() >= 3;
  }

  /** Reads the next bin name. */
  String bin() {
    return words.poll();
  }

  /** Reads the next quantity. */
  int qty() {
    return Integer.parseInt(words.poll());
  }
}
