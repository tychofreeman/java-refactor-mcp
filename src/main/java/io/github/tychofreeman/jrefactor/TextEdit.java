package io.github.tychofreeman.jrefactor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A replacement of the text from {@code start} to {@code end} by {@code text}, in file offsets. */
record TextEdit(int start, int end, String text) {

  /**
   * {@code text}, which starts at {@code offset} of its file, with {@code edits} applied. The edits
   * must not overlap; they are applied from the end, so each offset stays valid.
   */
  static String apply(String text, List<TextEdit> edits, int offset) {
    List<TextEdit> sorted = new ArrayList<>(edits);
    sorted.sort(
        Comparator.comparingInt(TextEdit::start).thenComparingInt(TextEdit::end).reversed());
    StringBuilder out = new StringBuilder(text);
    for (TextEdit e : sorted) {
      out.replace(e.start() - offset, e.end() - offset, e.text());
    }
    return out.toString();
  }
}
