package io.github.tychofreeman.jrefactor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  /**
   * The headers declaring one name: their hash, and the type names their text mentions (a type's
   * own name left out), through which a change to those types reaches this declaration.
   */
  record Decl(String hash, Set<String> types) {}

  /**
   * The headers of {@code unit}, parsed from {@code text}: a type by its simple name ({@code
   * "Shop"}), a member by its type's and its own ({@code "Shop#total"}).
   */
  static Map<String, Decl> of(String text, CompilationUnit unit) {
    Map<String, StringBuilder> parts = new TreeMap<>();
    for (Object o : unit.types()) {
      type((AbstractTypeDeclaration) o, text, parts);
    }
    String pkg = unit.getPackage() == null ? "" : unit.getPackage().getName().toString();
    Map<String, Decl> out = new TreeMap<>();
    parts.forEach((key, headers) -> {
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
      Set<String> types = new TreeSet<>();
      for (String id : used) {
        if (Character.isUpperCase(id.charAt(0)) && !id.equals(key)) {
          types.add(id);
        }
      }
      out.put(key, new Decl(hash(context + header), types));
    });
    return out;
  }

  /** The keys whose headers differ between {@code before} and {@code after}, added and removed. */
  static Set<String> changed(Map<String, Decl> before, Map<String, Decl> after) {
    Set<String> out = new HashSet<>();
    for (Map.Entry<String, Decl> e : before.entrySet()) {
      Decl now = after.get(e.getKey());
      if (now == null || !now.hash().equals(e.getValue().hash())) {
        out.add(e.getKey());
      }
    }
    for (String key : after.keySet()) {
      if (!before.containsKey(key)) {
        out.add(key);
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

  /**
   * Whether {@code text} creates one of {@code types} ({@code new Base(}), as an anonymous subclass
   * may.
   */
  static boolean creates(String text, Set<String> types) {
    Set<String> news = new HashSet<>();
    for (String t : types) {
      news.add("new " + t);
    }
    return mentions(text, news);
  }

  private static void type(
      AbstractTypeDeclaration t, String text, Map<String, StringBuilder> parts) {
    String owner = t.getName().getIdentifier();
    int open = text.indexOf('{', t.getName().getStartPosition());
    add(parts, owner, text.substring(start(t), open < 0 ? Nodes.end(t) : open));
    if (t instanceof EnumDeclaration e) {
      for (Object o : e.enumConstants()) {
        EnumConstantDeclaration c = (EnumConstantDeclaration) o;
        add(
            parts,
            owner + "#" + c.getName().getIdentifier(),
            text.substring(start(c), Nodes.end(c)));
      }
    }
    for (Object o : t.bodyDeclarations()) {
      if (o instanceof AbstractTypeDeclaration inner) {
        type(inner, text, parts);
      } else if (o instanceof MethodDeclaration m) {
        int end = m.getBody() == null ? Nodes.end(m) : m.getBody().getStartPosition();
        add(parts, owner + "#" + m.getName().getIdentifier(), text.substring(start(m), end));
      } else if (o instanceof FieldDeclaration f) {
        for (Object v : f.fragments()) {
          String name = ((VariableDeclarationFragment) v).getName().getIdentifier();
          add(parts, owner + "#" + name, text.substring(start(f), Nodes.end(f)));
        }
      } else if (o instanceof AnnotationTypeMemberDeclaration a) {
        add(
            parts,
            owner + "#" + a.getName().getIdentifier(),
            text.substring(start(a), Nodes.end(a)));
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
