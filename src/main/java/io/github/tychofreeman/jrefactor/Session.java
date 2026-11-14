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

  /**
   * The changes to exceptional behavior the running call made and reports, in order: written by
   * {@link Exceptional} only, and kept or dropped by the call's {@link Scope}.
   */
  List<String> exceptional = new ArrayList<>();

  /** The library classes by simple name, built on first use; null until then. */
  Map<String, List<String>> libraries;

  /** The last type check, with the text of every file it counted. */
  TypeCheck.Counted lastCount;

  /** The files the last type check parsed, for tests of the narrowing. */
  List<Path> lastParsed = List.of();

  /** How many times the project has been parsed for a type check, for tests of the reuse. */
  int parses;

  /** The steps of the last batch that was refused, for a retry to reuse; null after one passes. */
  List<Map<String, Object>> refusedBatch;

  /** The check that batch ran with. */
  String refusedCheck;

  /**
   * Whether the statement edits take typed code only, without {@code build} (an A/B arm, issue
   * #376): set by {@code JREFACTOR_NO_BUILD=1}, the server publishes them as they were before it.
   */
  boolean noBuild = "1".equals(System.getenv("JREFACTOR_NO_BUILD"));

  /**
   * Whether gorefactor's names for the generators are published as tools of their own (an A/B arm,
   * issue #376): set by {@code JREFACTOR_PUBLISH_ALIASES=1}.
   */
  boolean publishAliases = "1".equals(System.getenv("JREFACTOR_PUBLISH_ALIASES"));

  /**
   * How many binding parses have run (each {@link Jdt#parse}, and each {@link Jdt#parseAll} batch),
   * for {@code GateCostTest}.
   */
  int bindingParses;

  /** How many calls are running, one inside another (a batch's steps inside the batch). */
  private int depth;

  /**
   * The declarations the running batch's later steps delete (replace_declaration with empty
   * content): a use inside one does not survive the batch, so {@link SafeDelete} does not count it.
   */
  List<SafeDelete.Deletion> deletedLater = List.of();

  /** What {@link #deferring} and the like return: closing it puts back what it changed. */
  interface Restore extends AutoCloseable {
    @Override
    void close();
  }

  /**
   * Sets {@link #deferred} until the returned {@link Restore} closes, then puts back what it was.
   */
  Restore deferring() {
    boolean outer = deferred;
    deferred = true;
    return () -> deferred = outer;
  }

  /**
   * Opens one call's scope: {@code edit} and {@code preserve} hold while it runs, with a list of
   * its own for its changes to exceptional behavior. Closing it puts back the outer call's flags.
   */
  Scope scope(boolean edit, boolean preserve) {
    return new Scope(edit, preserve);
  }

  /** The flags of one running call ({@link #scope}), put back when it closes. */
  final class Scope implements AutoCloseable {
    private final boolean edit;
    private final boolean outerEditing;
    private final boolean outerPreserve;
    private final List<String> outerExceptional;
    private final List<String> reports = new ArrayList<>();
    private boolean kept;

    private Scope(boolean edit, boolean preserve) {
      this.edit = edit;
      outerEditing = editing;
      outerPreserve = preserveExceptions;
      outerExceptional = exceptional;
      editing = edit;
      preserveExceptions = preserve;
      exceptional = reports;
      depth++;
    }

    /** The changes to exceptional behavior the call has reported. */
    List<String> reports() {
      return reports;
    }

    /** Keeps the call's reports, which become the outer call's too (a batch's, for its steps). */
    void keep() {
      kept = true;
    }

    /**
     * Puts back the outer call's flags. Inside a batch, a step that was an edit leaves the batch
     * editing, for its check at the end; after a call at the top, nothing is.
     */
    @Override
    public void close() {
      depth--;
      if (kept) {
        outerExceptional.addAll(reports);
      }
      editing = outerEditing || depth > 0 && edit;
      preserveExceptions = outerPreserve;
      exceptional = outerExceptional;
    }
  }
}
