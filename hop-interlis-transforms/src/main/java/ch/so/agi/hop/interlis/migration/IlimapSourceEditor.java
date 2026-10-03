package ch.so.agi.hop.interlis.migration;

import guru.interlis.transformer.mapping.ilimap.editor.IlimapHighlighter;
import guru.interlis.transformer.mapping.ilimap.parser.IlimapParser;
import java.util.ArrayList;
import java.util.EnumMap;
import org.apache.hop.ui.core.gui.GuiResource;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.widgets.Composite;

/** Lexical styling stays available while the parser reports an incomplete edit. */
final class IlimapSourceEditor {
  final StyledText text;
  private final EnumMap<IlimapHighlighter.Kind, Color> colors =
      new EnumMap<>(IlimapHighlighter.Kind.class);
  private final IlimapHighlighter highlighter = new IlimapHighlighter();
  private int errorOffset = -1;
  private int[] brackets = new int[0];

  IlimapSourceEditor(Composite parent) {
    text = new StyledText(parent, SWT.BORDER | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
    text.setTabs(2);
    text.setLeftMargin(52);
    boolean dark = parent.getBackground().getRed() < 128;
    colors.put(
        IlimapHighlighter.Kind.KEYWORD,
        color(dark ? new RGB(184, 156, 255) : new RGB(115, 45, 160)));
    colors.put(
        IlimapHighlighter.Kind.FUNCTION,
        color(dark ? new RGB(110, 196, 255) : new RGB(0, 85, 150)));
    colors.put(
        IlimapHighlighter.Kind.STRING, color(dark ? new RGB(153, 215, 151) : new RGB(32, 112, 49)));
    colors.put(
        IlimapHighlighter.Kind.ENUM, color(dark ? new RGB(240, 197, 125) : new RGB(145, 78, 10)));
    colors.put(IlimapHighlighter.Kind.NUMBER, colors.get(IlimapHighlighter.Kind.ENUM));
    colors.put(
        IlimapHighlighter.Kind.COMMENT,
        color(dark ? new RGB(165, 174, 181) : new RGB(104, 112, 120)));
    text.setFont(GuiResource.getInstance().getFontFixed());
    text.addDisposeListener(e -> colors.values().stream().distinct().forEach(Color::dispose));
    text.addPaintListener(
        e -> {
          e.gc.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));
          int first = text.getTopIndex();
          for (int line = first; line < text.getLineCount(); line++) {
            int y = text.getLinePixel(line);
            if (y > text.getClientArea().height) break;
            e.gc.drawText(Integer.toString(line + 1), 6, y, true);
          }
          e.gc.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_BLUE));
          for (int offset : brackets) {
            var point = text.getLocationAtOffset(offset);
            if (point.x >= text.getLeftMargin())
              e.gc.drawRectangle(point.x, point.y, e.gc.textExtent("(").x, text.getLineHeight());
          }
        });
    text.addCaretListener(
        e -> {
          brackets = matchingBrackets(text.getText(), e.caretOffset);
          text.redraw();
        });
    text.addVerifyListener(
        e -> {
          if (e.text.equals("\n") || e.text.equals("\r") || e.text.equals("\r\n")) {
            int line = text.getLineAtOffset(e.start);
            String prefix =
                text.getTextRange(text.getOffsetAtLine(line), e.start - text.getOffsetAtLine(line));
            String indent = prefix.substring(0, prefix.length() - prefix.stripLeading().length());
            e.text = "\n" + indent + (prefix.stripTrailing().endsWith("{") ? "  " : "");
          }
        });
  }

  private Color color(RGB rgb) {
    return new Color(text.getDisplay(), rgb);
  }

  void highlight() {
    errorOffset = -1;
    try {
      new IlimapParser(text.getText()).parseDocument();
    } catch (IlimapParser.ParseException ex) {
      errorOffset = Math.min(ex.position.offset(), text.getCharCount() - 1);
    } catch (RuntimeException ex) {
      /* incomplete token; retain lexical highlighting */
    }
    var styles = new ArrayList<StyleRange>();
    for (var span : highlighter.highlight(text.getText())) {
      StyleRange style =
          new StyleRange(span.start(), span.end() - span.start(), colors.get(span.kind()), null);
      if (span.kind() == IlimapHighlighter.Kind.KEYWORD) style.fontStyle = SWT.BOLD;
      if (errorOffset >= span.start() && errorOffset < span.end()) {
        style.underline = true;
        style.underlineStyle = SWT.UNDERLINE_ERROR;
        style.underlineColor = text.getDisplay().getSystemColor(SWT.COLOR_RED);
      }
      styles.add(style);
    }
    text.setStyleRanges(styles.toArray(StyleRange[]::new));
    if (errorOffset >= 0) {
      StyleRange style = text.getStyleRangeAtOffset(errorOffset);
      style = style == null ? new StyleRange() : (StyleRange) style.clone();
      style.start = errorOffset;
      style.length = 1;
      style.underline = true;
      style.underlineStyle = SWT.UNDERLINE_ERROR;
      style.underlineColor = text.getDisplay().getSystemColor(SWT.COLOR_RED);
      text.setStyleRange(style);
    }
    brackets = matchingBrackets(text.getText(), text.getCaretOffset());
    text.redraw();
  }

  static int[] matchingBrackets(String source, int caret) {
    boolean[] literal = new boolean[source.length()];
    for (var span : new IlimapHighlighter().highlight(source))
      if (span.kind() == IlimapHighlighter.Kind.STRING
          || span.kind() == IlimapHighlighter.Kind.COMMENT)
        java.util.Arrays.fill(literal, span.start(), span.end(), true);
    int start = caret > 0 && "(){}".indexOf(source.charAt(caret - 1)) >= 0 ? caret - 1 : caret;
    if (start >= source.length() || literal[start]) return new int[0];
    char c = source.charAt(start);
    int kind = "({)}".indexOf(c);
    if (kind < 0) return new int[0];
    int step = kind < 2 ? 1 : -1;
    char other = "})({".charAt("{()}".indexOf(c));
    int depth = 0;
    for (int i = start; i >= 0 && i < source.length(); i += step) {
      if (literal[i]) continue;
      if (source.charAt(i) == c) depth++;
      if (source.charAt(i) == other && --depth == 0) return new int[] {start, i};
    }
    return new int[0];
  }
}
