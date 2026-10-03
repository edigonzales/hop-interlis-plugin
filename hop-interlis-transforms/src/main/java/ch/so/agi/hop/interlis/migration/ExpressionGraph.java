package ch.so.agi.hop.interlis.migration;

import guru.interlis.transformer.expr.*;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

/** Focused expression view generated from the expression AST, never from regex dependencies. */
final class ExpressionGraph {
  final Canvas canvas;
  private final List<Node> nodes = new ArrayList<>();
  private String error;

  private record Node(String label, int depth, int row, int parent) {}

  ExpressionGraph(Composite parent, Runnable edit) {
    canvas = new Canvas(parent, SWT.BORDER | SWT.DOUBLE_BUFFERED);
    canvas.setToolTipText(
        "Expression graph; double-click to edit the complete expression. Lazy conditional branches"
            + " keep their semantics.");
    canvas.addListener(SWT.MouseDoubleClick, e -> edit.run());
    canvas.addPaintListener(e -> draw(e.gc));
  }

  void show(String target, String expression) {
    nodes.clear();
    error = null;
    if (expression == null || expression.isBlank()) {
      error = "Select an assignment to inspect its function graph";
      canvas.redraw();
      return;
    }
    try {
      nodes.add(new Node(target, 0, 0, -1));
      append(ExpressionParser.parse(expression), 1, 0);
    } catch (RuntimeException ex) {
      error = "Expression remains available in the DSL: " + expression;
    }
    canvas.redraw();
  }

  private void append(Expression expression, int depth, int parent) {
    String label =
        switch (expression) {
          case FunctionCallExpr f -> f.functionName() + "(…)";
          case PathExpr p -> p.alias() + "." + p.attributeName();
          case LiteralExpr l -> String.valueOf(l.value().toNative());
          case ConditionalExpr ignored -> "if / then / else (lazy)";
        };
    int index = nodes.size();
    nodes.add(new Node(label, depth, index, parent));
    if (expression instanceof FunctionCallExpr f)
      for (Expression argument : f.arguments()) append(argument, depth + 1, index);
    if (expression instanceof ConditionalExpr c) {
      append(c.condition(), depth + 1, index);
      append(c.thenExpr(), depth + 1, index);
      append(c.elseExpr(), depth + 1, index);
    }
  }

  private void draw(GC gc) {
    if (error != null) {
      gc.drawText(error, 12, 14, true);
      return;
    }
    int maxDepth = nodes.stream().mapToInt(Node::depth).max().orElse(0);
    int width = Math.max(150, canvas.getClientArea().width / (maxDepth + 1));
    int height =
        Math.max(28, Math.min(38, canvas.getClientArea().height / Math.max(1, nodes.size())));
    for (Node node : nodes) {
      int x = (maxDepth - node.depth()) * width + 8, y = node.row() * height + 8;
      if (node.parent() >= 0) {
        Node parent = nodes.get(node.parent());
        int px = (maxDepth - parent.depth()) * width + 8, py = parent.row() * height + 8;
        gc.drawLine(x + width - 20, y + 12, px, py + 12);
      }
      gc.drawRoundRectangle(x, y, width - 20, 25, 6, 6);
      String label = node.label();
      while (label.length() > 3 && gc.textExtent(label).x > width - 30)
        label = label.substring(0, label.length() - 2) + "…";
      gc.drawText(label, x + 5, y + 4, true);
    }
  }
}
