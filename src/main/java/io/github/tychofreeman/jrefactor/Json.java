package io.github.tychofreeman.jrefactor;

import tools.jackson.databind.ObjectMapper;

/** The one JSON mapper: arguments, results, the journal, caches and the session log. */
final class Json {

  /** Jackson's default mapper, shared: it is immutable and thread-safe. */
  static final ObjectMapper MAPPER = new ObjectMapper();

  private Json() {}
}
