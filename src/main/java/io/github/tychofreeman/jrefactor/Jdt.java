package io.github.tychofreeman.jrefactor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FileASTRequestor;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * Headless JDT Core parsing with bindings resolved. No workspace, no OSGi runtime. Validated by
 * spikes/jdt-headless.
 */
final class Jdt {

  static final String SOURCE_LEVEL = JavaCore.VERSION_21;

  private Jdt() {}

  /** The property of a unit parsed from a project's file that holds the file's absolute path. */
  static final String FILE = "jrefactor.file";

  /** A single static import; group 1 is the imported name, qualified. */
  private static final Pattern STATIC_IMPORT =
      Pattern.compile("^\\s*import\\s+static\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

  /** The property of a unit that holds the text it was parsed from, when the parse was given it. */
  static final String SOURCE = "jrefactor.source";

  /** The name of the field {@link #withProbe} declares, which no source uses. */
  static final String PROBE = "jrefactorProbe";

  /** The file {@code unit} was parsed from, or null for text parsed alone. */
  static Path fileOf(CompilationUnit unit) {
    return unit.getProperty(FILE) instanceof Path p ? p : null;
  }

  /** Parses {@code source} without resolving anything: its problems are syntax errors only. */
  static CompilationUnit parseSyntax(String source) {
    CompilationUnit unit =
        (CompilationUnit) syntaxParser(ASTParser.K_COMPILATION_UNIT, source).createAST(null);
    unit.setProperty(SOURCE, source);
    return unit;
  }

  /** {@code content} parsed as the members of a type, syntax only. */
  static ASTNode parseMembers(String content) {
    return syntaxParser(ASTParser.K_CLASS_BODY_DECLARATIONS, content).createAST(null);
  }

  /** A parser of {@code kind} for {@code source}, with the options every parser here uses. */
  private static ASTParser syntaxParser(int kind, String source) {
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setKind(kind);
    parser.setSource(source.toCharArray());
    parser.setCompilerOptions(options());
    return parser;
  }

  /** The compiler options for {@link #SOURCE_LEVEL}: the source level, compliance and target. */
  private static Map<String, String> options() {
    Map<String, String> options = JavaCore.getOptions();
    JavaCore.setComplianceOptions(SOURCE_LEVEL, options);
    return options;
  }

  /**
   * Parses {@code source}, the text of {@code file} inside {@code project}. Types from other files
   * resolve from the project's source path; library types resolve only from the project's
   * classpath. Problems are recovered, not thrown.
   */
  static CompilationUnit parse(SourceProject project, Path file, String source) {
    ASTParser parser = parser(project);
    parser.setKind(ASTParser.K_COMPILATION_UNIT);
    parser.setSource(source.toCharArray());
    parser.setUnitName(project.relative(file));
    CompilationUnit unit;
    try {
      unit = (CompilationUnit) parser.createAST(null);
    } catch (RuntimeException e) {
      throw compilerFailed(e);
    }
    unit.setProperty(FILE, file.toAbsolutePath().normalize());
    unit.setProperty(SOURCE, source);
    return unit;
  }

  /**
   * Parses {@code files} of {@code project} as they are on disk, in one batch that shares one
   * environment, and hands each unit to {@code each} as it is done. The same units as {@link
   * #parse} of each file, many times faster on a large project: each type is resolved once, not
   * once per file that uses it. The files that declare a type they import a single static member of
   * are parsed with them, and not handed to {@code each}.
   */
  static void parseAll(
      SourceProject project, List<Path> files, BiConsumer<Path, CompilationUnit> each) {
    Set<Path> wanted = new HashSet<>(files);
    List<Path> batch = new ArrayList<>(files);
    batch.addAll(staticImported(project, files));
    String[] paths = batch.stream().map(Path::toString).toArray(String[]::new);
    String[] encodings = new String[paths.length];
    java.util.Arrays.fill(encodings, "UTF-8");
    RuntimeException[] fromEach = new RuntimeException[1];
    Set<String> completed = new HashSet<>();
    try {
      parser(project)
          .createASTs(
              paths,
              encodings,
              new String[0],
              new FileASTRequestor() {
                @Override
                public void acceptAST(String sourceFilePath, CompilationUnit ast) {
                  if (!wanted.contains(Path.of(sourceFilePath))) {
                    return;
                  }
                  ast.setProperty(FILE, Path.of(sourceFilePath).toAbsolutePath().normalize());
                  completeMembers(ast, completed);
                  try {
                    each.accept(Path.of(sourceFilePath), ast);
                  } catch (RuntimeException e) {
                    fromEach[0] = e;
                    throw e;
                  }
                }
              },
              null);
    } catch (RuntimeException e) {
      if (e == fromEach[0]) {
        throw e;
      }
      throw compilerFailed(e);
    }
  }

  /**
   * Each of {@code files} parsed as {@link #parseAll(SourceProject, List, BiConsumer)} does, in
   * their order.
   */
  static Map<Path, CompilationUnit> parseAll(SourceProject project, List<Path> files) {
    Map<Path, CompilationUnit> parsed = new java.util.HashMap<>();
    parseAll(project, files, parsed::put);
    Map<Path, CompilationUnit> ordered = new java.util.LinkedHashMap<>();
    for (Path file : files) {
      ordered.put(file, parsed.get(file));
    }
    return ordered;
  }

  /**
   * A parser for Java 21 that resolves bindings against the project's source path and classpath.
   */
  private static ASTParser parser(SourceProject project) {
    project.session().bindingParses++;
    ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
    parser.setResolveBindings(true);
    parser.setBindingsRecovery(true);
    parser.setCompilerOptions(options());
    String[] sourcepath =
        project.sourceRoots().stream()
            .filter(Files::isDirectory)
            .map(Path::toString)
            .toArray(String[]::new);
    String[] encodings = new String[sourcepath.length];
    java.util.Arrays.fill(encodings, "UTF-8"); // one per source path entry, or JDT throws
    parser.setEnvironment(project.classpath().toArray(String[]::new), sourcepath, encodings, true);
    return parser;
  }

  /**
   * The refusal for an exception the compiler threw while resolving: ECJ fails this way on some
   * code that does not compile, which it should have reported as a problem. Names the exception and
   * where in the compiler it was thrown.
   */
  static Refusal compilerFailed(RuntimeException e) {
    StackTraceElement[] trace = e.getStackTrace();
    String where =
        trace.length == 0
            ? ""
            : " (in " + trace[0].getClassName() + "." + trace[0].getMethodName() + ")";
    return new Refusal(
        "The Java compiler (ECJ) failed while type-checking, so nothing was changed: "
            + e
            + where
            + ". It fails this way on some code that does not compile, such as a lambda or method"
            + " reference whose target type no longer fits its type parameter's bounds. Change the"
            + " edit so the code it writes type-checks (another type, bound or argument); if every"
            + " edit fails this way, the project as it is trips the compiler: fix the code there"
            + " first.");
  }

  /**
   * The project's files, other than {@code files}, that declare a type {@code files} import a
   * single static member of. JDT looks up the binding of such an import only when it is asked for,
   * after the batch, and then needs every member of the imported type resolved; it has resolved
   * them only for a type of the batch, and for a type it read from the source path it throws
   * (#411).
   */
  static Set<Path> staticImported(SourceProject project, List<Path> files) {
    Set<Path> given = new HashSet<>();
    for (Path file : files) {
      given.add(file.toAbsolutePath().normalize());
    }
    Set<Path> out = new LinkedHashSet<>();
    for (Path file : files) {
      String text;
      try {
        text = Files.readString(file);
      } catch (IOException e) {
        continue; // the parse reports it
      }
      Matcher m = STATIC_IMPORT.matcher(text);
      while (m.find()) {
        List<String> parts = List.of(m.group(1).split("\\."));
        // The member's type is the longest prefix that names a file: p.Outer.Inner.m is in p/Outer.
        for (int k = parts.size() - 1; k > 0; k--) {
          Path declaring = project.fileDeclaring(String.join(".", parts.subList(0, k)));
          if (declaring != null) {
            if (!given.contains(declaring)) {
              out.add(declaring);
            }
            break;
          }
        }
      }
    }
    return out;
  }

  /**
   * Asks each project type {@code unit} names, and the types around, above and inside it, for its
   * members while the batch is running. JDT resolves a type it reads from the source path only as
   * far as the batch needs it and frees its scope when the batch ends; asked after that, the type
   * answers no methods (#431). {@code completed} holds the keys of the types already asked.
   */
  private static void completeMembers(CompilationUnit unit, Set<String> completed) {
    for (SimpleName name : Nodes.all(unit, SimpleName.class)) {
      switch (name.resolveBinding()) {
        case ITypeBinding t -> completeMembers(t, completed);
        case IMethodBinding m -> completeMembers(m.getDeclaringClass(), completed);
        case IVariableBinding v -> {
          completeMembers(v.getDeclaringClass(), completed);
          completeMembers(v.getType(), completed);
        }
        case null, default -> {}
      }
    }
  }

  /** {@link #completeMembers(CompilationUnit, Set)} of one type and the types it leads to. */
  private static void completeMembers(ITypeBinding type, Set<String> completed) {
    if (type == null) {
      return;
    }
    ITypeBinding t = type.isArray() ? type.getElementType() : type;
    t = t.getTypeDeclaration();
    if (!t.isFromSource()
        || t.isTypeVariable()
        || t.isCapture()
        || t.isWildcardType()
        || !completed.add(t.getKey())) {
      return;
    }
    t.getDeclaredFields();
    t.getDeclaredMethods();
    for (ITypeBinding member : t.getDeclaredTypes()) {
      completeMembers(member, completed);
    }
    completeMembers(t.getSuperclass(), completed);
    for (ITypeBinding i : t.getInterfaces()) {
      completeMembers(i, completed);
    }
    completeMembers(t.getDeclaringClass(), completed);
  }

  /**
   * The text {@code unit} was parsed from: the text given to the parse, else its file as it is on
   * disk, else null.
   */
  static String sourceOf(CompilationUnit unit) {
    if (unit.getProperty(SOURCE) instanceof String s) {
      return s;
    }
    Path file = fileOf(unit);
    if (file == null) {
      return null;
    }
    try {
      return Files.readString(file);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * The type {@code typeText} names in the body of {@code type} in {@code file} ({@code text}),
   * with {@code chosen} for ambiguous imports: a probe field of it ({@link #withProbe}), parsed
   * with the imports fixed. Null when the field has no binding; a name that does not resolve gives
   * a recovered type.
   */
  static ITypeBinding probeType(
      SourceProject project,
      Path file,
      String text,
      AbstractTypeDeclaration type,
      String typeText,
      List<String> chosen)
      throws IOException {
    String probe = withProbe(text, type, typeText);
    String fixed = Imports.fix(project, file, text, probe, chosen).text();
    for (VariableDeclarationFragment f :
        Nodes.all(parse(project, file, fixed), VariableDeclarationFragment.class)) {
      if (f.getName().getIdentifier().equals(PROBE) && f.resolveBinding() != null) {
        return f.resolveBinding().getType();
      }
    }
    return null;
  }

  /**
   * {@code text} with a field {@code typeText jrefactorProbe;} declared as a member of {@code
   * type}: before its first member, else at the end of its body, after a {@code ;} that ends an
   * enum's constants.
   */
  static String withProbe(String text, AbstractTypeDeclaration type, String typeText) {
    String field = typeText + " " + PROBE + "; ";
    List<?> members = type.bodyDeclarations();
    if (!members.isEmpty()) {
      int at = ((ASTNode) members.get(0)).getStartPosition();
      return text.substring(0, at) + field + text.substring(at);
    }
    int close = Nodes.end(type) - 1;
    String end = type instanceof EnumDeclaration ? "; " : " ";
    return text.substring(0, close) + end + field + text.substring(close);
  }
}
