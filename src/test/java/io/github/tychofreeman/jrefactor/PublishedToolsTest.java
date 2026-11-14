package io.github.tychofreeman.jrefactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The tools a server publishes, as its flags change them (the A/B arms of issue #376). */
class PublishedToolsTest {
  private static final List<String> BUILT =
      List.of("insert_statements", "replace_statements", "replace_expression");

  @Test
  void byDefaultEveryToolAsDefined() {
    Session session = new Session();
    assertEquals(Tools.ALL, Tools.published(session));
    assertSame(Tools.INSERT_STATEMENTS, Tools.named("insert_statements", session));
  }

  @Test
  void withoutBuildTheStatementEditsTakeTypedCodeOnly() {
    Session session = new Session();
    session.noBuild = true;
    List<Tools.Def> published = Tools.published(session);
    assertEquals(
        Tools.ALL.stream().map(Tools.Def::name).toList(),
        published.stream().map(Tools.Def::name).toList());
    for (Tools.Def def : published) {
      if (BUILT.contains(def.name())) {
        assertFalse(def.args().contains("build"), def.name());
        assertFalse(def.schema().contains("build"), def.name());
        assertFalse(def.description().contains("build"), def.name());
        assertSame(def, Tools.named(def.name(), session));
      } else {
        assertTrue(Tools.ALL.contains(def), def.name());
      }
    }
  }

  @Test
  void withoutBuildTheAliasesThatBuildAreRefused() {
    Session session = new Session();
    session.noBuild = true;
    for (String alias : List.of("create_loop", "create_call", "create_guard")) {
      Refusal refusal = assertThrows(Refusal.class, () -> Tools.named(alias, session));
      assertTrue(refusal.getMessage().startsWith("No tool " + alias + "."), refusal.getMessage());
    }
    assertSame(Tools.REPLACE_PARAMETER, Tools.named("replace_parameter", session));
  }
}
