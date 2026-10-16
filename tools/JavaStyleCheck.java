import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Syntax-only conventions check. Requires a JDK, but no Android SDK or parser dependency. */
public final class JavaStyleCheck {
    private JavaStyleCheck() {}

    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: JavaStyleCheck <UTF-8 source-file manifest>");
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK 17 or newer is required (not a JRE).");
        }
        final List<Path> files = new ArrayList<>();
        for (final String line : Files.readAllLines(Path.of(args[0]), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                files.add(Path.of(line));
            }
        }
        final DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        int issues = 0;
        try (final StandardJavaFileManager manager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            final JavacTask task =
                    (JavacTask)
                            compiler.getTask(
                                    null,
                                    manager,
                                    diagnostics,
                                    List.of("-proc:none"),
                                    null,
                                    manager.getJavaFileObjectsFromPaths(files));
            final SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (final CompilationUnitTree unit : task.parse()) {
                final Rules rules = new Rules(unit, positions);
                rules.scan(unit, null);
                issues += rules.issues;
            }
        }
        for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                System.err.println(diagnostic);
                issues++;
            }
        }
        if (issues != 0) {
            System.err.println("Java syntax/style checks failed: " + issues + " issue(s).");
            System.exit(1);
        }
        System.out.println("Java syntax/style checks passed (" + files.size() + " files).");
    }

    private static final class Rules extends TreePathScanner<Void, Void> {
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final Set<Long> declarations = new HashSet<>();
        private int issues;

        Rules(final CompilationUnitTree unit, final SourcePositions positions) {
            this.unit = unit;
            this.positions = positions;
        }

        private void report(final Tree tree, final String message) {
            final long position = positions.getStartPosition(unit, tree);
            final long line = position >= 0 ? unit.getLineMap().getLineNumber(position) : 1;
            System.err.println(unit.getSourceFile().getName() + ":" + line + ": " + message);
            issues++;
        }

        private void finalParameter(final VariableTree parameter) {
            if (positions.getEndPosition(unit, parameter) >= 0
                    && !parameter.getModifiers().getFlags().contains(Modifier.FINAL)) {
                report(parameter, "Parameter must be final: " + parameter.getName());
            }
        }

        private void braces(final StatementTree statement) {
            if (!(statement instanceof BlockTree)) {
                report(statement, "Control-flow body must use braces.");
            }
        }

        @Override
        public Void visitMethod(final MethodTree node, final Void unused) {
            for (final VariableTree parameter : node.getParameters()) {
                finalParameter(parameter);
            }
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitLambdaExpression(final LambdaExpressionTree node, final Void unused) {
            for (final VariableTree parameter : node.getParameters()) {
                finalParameter(parameter);
                if (parameter.getType() == null || "var".equals(parameter.getType().toString())) {
                    report(parameter, "Lambda parameters must have explicit types.");
                }
            }
            return super.visitLambdaExpression(node, unused);
        }

        @Override
        public Void visitCatch(final CatchTree node, final Void unused) {
            finalParameter(node.getParameter());
            return super.visitCatch(node, unused);
        }

        @Override
        public Void visitTry(final TryTree node, final Void unused) {
            for (final Tree resource : node.getResources()) {
                if (resource instanceof VariableTree) {
                    final VariableTree declaration = (VariableTree) resource;
                    if (!declaration.getModifiers().getFlags().contains(Modifier.FINAL)) {
                        report(declaration, "Resource declarations must be final.");
                    }
                }
                // Java 9 references to existing resources have no declaration/modifier to change.
            }
            return super.visitTry(node, unused);
        }

        @Override
        public Void visitVariable(final VariableTree node, final Void unused) {
            if (node.getType() == null
                            && !(getCurrentPath().getParentPath().getLeaf()
                                    instanceof LambdaExpressionTree)
                    || node.getType() != null && "var".equals(node.getType().toString())) {
                report(node, "Use an explicit type instead of var.");
            }
            final long start = positions.getStartPosition(unit, node);
            final Tree parent = getCurrentPath().getParentPath().getLeaf();
            if (!(parent instanceof ForLoopTree) && start >= 0 && !declarations.add(start)) {
                report(node, "Declare one field/local per statement.");
            }
            return super.visitVariable(node, unused);
        }

        @Override
        public Void visitImport(final ImportTree node, final Void unused) {
            if (node.getQualifiedIdentifier().toString().endsWith(".*")) {
                report(node, "Use explicit imports instead of wildcard imports.");
            }
            return super.visitImport(node, unused);
        }

        @Override
        public Void visitIf(final IfTree node, final Void unused) {
            braces(node.getThenStatement());
            final StatementTree alternative = node.getElseStatement();
            if (alternative != null && !(alternative instanceof IfTree)) {
                braces(alternative);
            }
            return super.visitIf(node, unused);
        }

        @Override
        public Void visitForLoop(final ForLoopTree node, final Void unused) {
            braces(node.getStatement());
            return super.visitForLoop(node, unused);
        }

        @Override
        public Void visitEnhancedForLoop(final EnhancedForLoopTree node, final Void unused) {
            braces(node.getStatement());
            return super.visitEnhancedForLoop(node, unused);
        }

        @Override
        public Void visitWhileLoop(final WhileLoopTree node, final Void unused) {
            braces(node.getStatement());
            return super.visitWhileLoop(node, unused);
        }

        @Override
        public Void visitDoWhileLoop(final DoWhileLoopTree node, final Void unused) {
            braces(node.getStatement());
            return super.visitDoWhileLoop(node, unused);
        }
    }
}
