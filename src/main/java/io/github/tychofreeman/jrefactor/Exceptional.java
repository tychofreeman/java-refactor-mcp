package io.github.tychofreeman.jrefactor;

/**
 * Changes a refactoring makes to exceptional behavior (where an exception is thrown, or whether one
 * is), which it does not keep by default (docs/design.md, Decisions): each is reported in a part of
 * the result of its own, or, under preserve_exceptions, kept by the tool or refused.
 */
final class Exceptional {

  /** The argument every refactoring takes. */
  static final String FLAG = "preserve_exceptions";

  private Exceptional() {}

  /** Whether the running call asked to keep exceptional behavior. */
  static boolean preserved(SourceProject project) {
    return project.session().preserveExceptions;
  }

  /**
   * Reports {@code change}; under preserve_exceptions, refuses it instead, saying how to keep it:
   * {@code keep}.
   */
  static void change(SourceProject project, String change, String keep) {
    if (preserved(project)) {
      throw new Refusal(change + ". preserve_exceptions is set, so nothing was changed: " + keep);
    }
    project.session().exceptional.add(change);
  }

  /** Reports {@code change}, which the tool has kept when preserve_exceptions is set. */
  static void report(SourceProject project, String change) {
    if (!preserved(project)) {
      project.session().exceptional.add(change);
    }
  }

  /**
   * Runs {@code body} as a trial: returns the refusal it threw, or null when it passed. The changes
   * a refused trial reported are dropped, so a tool can try a move and fall back without them.
   */
  static Refusal trial(SourceProject project, Runnable body) {
    java.util.List<String> reports = project.session().exceptional;
    int mark = reports.size();
    try {
      body.run();
      return null;
    } catch (Refusal r) {
      reports.subList(mark, reports.size()).clear();
      return r;
    }
  }
}
