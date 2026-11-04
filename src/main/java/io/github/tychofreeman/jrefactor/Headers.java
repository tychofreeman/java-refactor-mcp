package io.github.tychofreeman.jrefactor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeMemberDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * What a Java file declares that other files can see: for each simple name it declares (its types,
 * their members and member types, nothing inside a body), a hash of the headers that declare it,
 * with the package and the imports each header names. A header is a type's text up to its body, a
 * method's up to its body, a field or enum constant whole; Javadoc is left out. A change that
 * leaves every header alone can change compile errors only in its own file; one that changes a
 * header can reach another file only through the name, which that file then mentions.
 */
final class Headers {

  private Headers() {}

  private static final Pattern IDENTIFIER = Pattern.compile("[\\p{L}_$][\\p{L}\\p{N}_$]*");

  /** The headers of {@code unit}, parsed from {@code text}, by the simple name they declare. */
  static Map<String, String> of(String text, CompilationUnit unit) {
    Map<String, StringBuilder> parts = new TreeMap<>();
    for (Object o : unit.types()) {
      type((AbstractTypeDeclaration) o, text, parts);
    }
    String pkg = unit.getPackage() == null ? "" : unit.getPackage().getName().toString();
    Map<String, String> out = new TreeMap<>();
    parts.forEach((name, headers) -> {
      String header = headers.toString();
      Set<String> used = identifiers(header);
      StringBuilder context = new StringBuilder(pkg).append('\n');
      for (Object o : unit.imports()) {
        ImportDeclaration i = (ImportDeclaration) o;
        String qualified = i.getName().getFullyQualifiedName();
        String last = qualified.substring(qualified.lastIndexOf('.') + 1);
        if (i.isOnDemand() || used.contains(last)) {
          context.append(i.isStatic() ? "static " : "").append(qualified);
          context.append(i.isOnDemand() ? ".*\n" : "\n");
        }
      }
      out.put(name, hash(context + header));
    });
    return out;
  }

  /** The names whose headers differ between {@code before} and {@code after}, added and removed. */
  static Set<String> changed(Map<String, String> before, Map<String, String> after) {
    Set<String> out = new HashSet<>();
    for (Map.Entry<String, String> e : before.entrySet()) {
      if (!e.getValue().equals(after.get(e.getKey()))) {
        out.add(e.getKey());
      }
    }
    for (String name : after.keySet()) {
      if (!before.containsKey(name)) {
        out.add(name);
      }
    }
    return out;
  }

  /** Whether {@code text} has one of {@code names} as a whole identifier. */
  static boolean mentions(String text, Set<String> names) {
    for (String name : names) {
      for (int at = text.indexOf(name); at >= 0; at = text.indexOf(name, at + 1)) {
        boolean starts = at == 0 || !Character.isJavaIdentifierPart(text.charAt(at - 1));
        int end = at + name.length();
        boolean ends = end == text.length() || !Character.isJavaIdentifierPart(text.charAt(end));
        if (starts && ends) {
          return true;
        }
      }
    }
    return false;
  }

  private static void type(
      AbstractTypeDeclaration t, String text, Map<String, StringBuilder> parts) {
    int open = text.indexOf('{', t.getName().getStartPosition());
    add(parts, t.getName().getIdentifier(), text.substring(start(t), open < 0 ? end(t) : open));
    if (t instanceof EnumDeclaration e) {
      for (Object o : e.enumConstants()) {
        EnumConstantDeclaration c = (EnumConstantDeclaration) o;
        add(parts, c.getName().getIdentifier(), text.substring(start(c), end(c)));
      }
    }
    for (Object o : t.bodyDeclarations()) {
      if (o instanceof AbstractTypeDeclaration inner) {
        type(inner, text, parts);
      } else if (o instanceof MethodDeclaration m) {
        int end = m.getBody() == null ? end(m) : m.getBody().getStartPosition();
        add(parts, m.getName().getIdentifier(), text.substring(start(m), end));
      } else if (o instanceof FieldDeclaration f) {
        for (Object v : f.fragments()) {
          String name = ((VariableDeclarationFragment) v).getName().getIdentifier();
          add(parts, name, text.substring(start(f), end(f)));
        }
      } else if (o instanceof AnnotationTypeMemberDeclaration a) {
        add(parts, a.getName().getIdentifier(), text.substring(start(a), end(a)));
      }
    }
  }

  private static void add(Map<String, StringBuilder> parts, String name, String header) {
    parts.computeIfAbsent(name, k -> new StringBuilder()).append(header).append('\n');
  }

  /** Where {@code d} starts after its Javadoc. */
  private static int start(BodyDeclaration d) {
    return d.getJavadoc() == null
        ? d.getStartPosition()
        : d.getJavadoc().getStartPosition() + d.getJavadoc().getLength();
  }

  private static int end(ASTNode node) {
    return node.getStartPosition() + node.getLength();
  }

  private static Set<String> identifiers(String text) {
    Set<String> out = new HashSet<>();
    Matcher m = IDENTIFIER.matcher(text);
    while (m.find()) {
      out.add(m.group());
    }
    return out;
  }

  private static String hash(String text) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest, 0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
