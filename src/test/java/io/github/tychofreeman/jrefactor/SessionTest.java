package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@SuppressWarnings("try") // scopes opened only to be closed
class SessionTest {

  @Test
  void aScopeSetsItsFlagsAndPutsBackTheOuterOnes() {
    Session session = new Session();
    try (Session.Scope batch = session.scope(false, true)) {
      assertFalse(session.editing);
      assertTrue(session.preserveExceptions);
      try (Session.Scope step = session.scope(true, false)) {
        assertTrue(session.editing);
        assertFalse(session.preserveExceptions);
      }
      assertTrue(session.editing, "a step that edited leaves its batch editing");
      assertTrue(session.preserveExceptions);
    }
    assertFalse(session.editing);
    assertFalse(session.preserveExceptions);
  }

  @Test
  void aScopeKeepsItsReportsOnlyWhenKept() {
    Session session = new Session();
    try (Session.Scope batch = session.scope(false, false)) {
      try (Session.Scope step = session.scope(false, false)) {
        session.exceptional.add("kept");
        step.keep();
      }
      try (Session.Scope step = session.scope(false, false)) {
        session.exceptional.add("dropped");
      }
      assertEquals(List.of("kept"), batch.reports());
    }
    assertEquals(List.of(), session.exceptional);
  }

  @Test
  void deferringPutsBackWhatItWas() {
    Session session = new Session();
    try (Session.Restore outer = session.deferring()) {
      try (Session.Restore inner = session.deferring()) {
        assertTrue(session.deferred);
      }
      assertTrue(session.deferred);
    }
    assertFalse(session.deferred);
  }

  @Test
  void aRefusedTrialDropsWhatItReported(@TempDir Path root) throws Exception {
    SourceProject project = SourceProject.of(root);
    Exceptional.report(project, "before");
    Refusal refused =
        Exceptional.trial(
            project,
            () -> {
              Exceptional.report(project, "tried");
              throw new Refusal("no");
            });
    assertEquals("no", refused.getMessage());
    assertNull(Exceptional.trial(project, () -> Exceptional.report(project, "passed")));
    assertEquals(List.of("before", "passed"), project.session().exceptional);
  }
}
