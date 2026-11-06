package io.github.tychofreeman.jrefactor;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * What the server remembers about one project while it runs: the undo history, the change the
 * running call makes, whether a batch defers its type check, the library index, and the last type
 * check. Each {@link SourceProject} has its own, so two projects, or two tests, share nothing. The
 * server runs one call at a time, so nothing here is guarded.
 */
final class Session {

  /** The changes made, the newest first, that {@code undo} can revert. */
  final Deque<Journal.Entry> done = new ArrayDeque<>();

  /** The change the running call is making, or null when no call runs. */
  Journal.Open open;

  /** True while a batch with check "end" runs: edits skip the type check it makes once after. */
  boolean deferred;

  /**
   * Whether the running change may declare stubs: the running call is an edit, or a batch one of
   * whose steps so far was. A refactoring's own output gets none: it changes no behavior, so a name
   * it leaves unresolved is its mistake, and a stub would hide it.
   */
  boolean editing;

  /**
   * Whether the running refactoring was asked to keep exceptional behavior (preserve_exceptions).
   */
  boolean preserveExceptions;

  /**
   * Whether a Java file may only start as a type's header (an experiment, roadmap #116): set by
   * {@code JREFACTOR_SKELETON_ONLY=1}, it refuses {@code create_file} content that declares
   * members.
   */
  boolean skeletonOnly = "1".equals(System.getenv("JREFACTOR_SKELETON_ONLY"));

  /** The changes to exceptional behavior the running call made and reports, in order. */
  List<String> exceptional = new ArrayList<>();

  /** The library classes by simple name, built on first use; null until then. */
  Map<String, List<String>> libraries;

  /** The last type check, with the text of every file it counted. */
  TypeCheck.Counted lastCount;

  /** The files the last type check parsed, for tests of the narrowing. */
  List<Path> lastParsed = List.of();

  /** How many times the project has been parsed for a type check, for tests of the reuse. */
  int parses;
}
