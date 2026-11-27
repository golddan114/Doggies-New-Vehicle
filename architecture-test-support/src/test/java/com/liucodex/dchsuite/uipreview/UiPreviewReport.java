package com.liucodex.dchsuite.uipreview;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * Headless geometry validator and deterministic PNG/HTML renderer for DCHSuite
 * UI layouts. Production screens supply the rectangles; this class never owns
 * gameplay or screen layout decisions.
 */
public final class UiPreviewReport {
  private static final Font FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 9);
  private static final int RENDER_SCALE = 2;

  private UiPreviewReport() {}

  /** Supplied by the consumer's runtime theme; the default is only a neutral geometry diagram. */
  public record Palette(String id, int panel, int section, int border, int button,
      int input, int text, int track, int thumb) {
    public Palette { Objects.requireNonNull(id, "palette id"); }
  }

  private static final Palette GEOMETRY = new Palette("geometry-only",
      0xFFE0E0E0, 0xFFCECECE, 0xFF707070, 0xFFD8D8D8,
      0xFFFFFFFF, 0xFF202020, 0xFFBBBBBB, 0xFF555555);

  public enum Kind {
    PANEL,
    REGION,
    BUTTON,
    FIELD,
    TEXT,
    SCROLLBAR
  }

  public record Rect(int x, int y, int width, int height) {
    public Rect {
      if (width < 0 || height < 0) {
        throw new IllegalArgumentException("preview rectangle extent is negative");
      }
    }

    public int right() {
      return x + width;
    }

    public int bottom() {
      return y + height;
    }

    public boolean contains(Rect other) {
      return other.x >= x && other.y >= y
          && other.right() <= right() && other.bottom() <= bottom();
    }

    public boolean intersects(Rect other) {
      return x < other.right() && right() > other.x
          && y < other.bottom() && bottom() > other.y;
    }
  }

  public record Glyph(List<String> pixels, int ink, int face) {
    public Glyph { pixels = List.copyOf(pixels); }

    void paint(Graphics2D graphics, Rect bounds) {
      graphics.setColor(new Color(ink, true));
      for (int y = 0; y < pixels.size(); y++) {
        String row = pixels.get(y);
        int left = bounds.x() + (bounds.width() - row.length()) / 2;
        for (int x = 0; x < row.length(); x++)
          if (row.charAt(x) == '#') graphics.fillRect(left + x, bounds.y() + (bounds.height() - pixels.size()) / 2 + y, 1, 1);
      }
    }
  }

  public record Node(
      String id,
      Kind kind,
      Rect bounds,
      String text,
      String parentId,
      boolean clipText,
      boolean collisionChecked, Glyph glyph) {
    public Node(String id, Kind kind, Rect bounds, String text, String parentId, boolean clipText, boolean collisionChecked) {
      this(id, kind, bounds, text, parentId, clipText, collisionChecked, null);
    }

    public Node {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("preview node id is blank");
      }
      kind = Objects.requireNonNull(kind, "kind");
      bounds = Objects.requireNonNull(bounds, "bounds");
      text = Objects.requireNonNullElse(text, "");
      parentId = Objects.requireNonNullElse(parentId, "");
    }

    public static Node panel(String id, Rect bounds) {
      return new Node(id, Kind.PANEL, bounds, "", "", true, false);
    }

    public static Node region(String id, Rect bounds, String parentId) {
      return new Node(id, Kind.REGION, bounds, "", parentId, true, false);
    }

    public static Node button(
        String id, Rect bounds, String text, String parentId) {
      return new Node(id, Kind.BUTTON, bounds, text, parentId, true, true);
    }

    public static Node field(
        String id, Rect bounds, String text, String parentId) {
      return new Node(id, Kind.FIELD, bounds, text, parentId, true, true);
    }

    public static Node text(
        String id,
        Rect allocation,
        String text,
        String parentId,
        boolean clipText) {
      return new Node(
          id, Kind.TEXT, allocation, text, parentId, clipText, true);
    }

    public static Node scrollbar(String id, Rect bounds, String parentId) {
      return new Node(
          id, Kind.SCROLLBAR, bounds, "", parentId, true, true);
    }
  }

  public record Scene(
      String id,
      String title,
      int width,
      int height,
      double guiScale,
      List<Node> nodes) {
    public Scene {
      if (id == null || id.isBlank() || width <= 0 || height <= 0
          || !Double.isFinite(guiScale) || guiScale < 1.0D) {
        throw new IllegalArgumentException("preview scene metadata is invalid");
      }
      title = Objects.requireNonNullElse(title, id);
      nodes = List.copyOf(nodes);
    }
  }

  public record Violation(
      String sceneId,
      String nodeId,
      String otherId,
      String message,
      Rect bounds) {}

  public record Result(Path index, List<Violation> violations) {
    public Result {
      violations = List.copyOf(violations);
    }

    public String summary() {
      if (violations.isEmpty()) {
        return "UI preview geometry passed: " + index;
      }
      StringBuilder result = new StringBuilder(
          "UI preview geometry failed; report: ").append(index);
      for (Violation violation : violations) {
        result.append(System.lineSeparator()).append(" - ")
            .append(violation.sceneId()).append(':')
            .append(violation.nodeId());
        if (!violation.otherId().isBlank()) {
          result.append(" <-> ").append(violation.otherId());
        }
        result.append(": ").append(violation.message());
      }
      return result.toString();
    }
  }

  public static Result write(
      Path outputDirectory, String title, List<Scene> scenes) throws IOException {
    return write(outputDirectory, title, scenes, GEOMETRY);
  }

  public static Result write(Path outputDirectory, String title, List<Scene> scenes,
      Palette palette) throws IOException {
    Objects.requireNonNull(palette, "palette");
    Files.createDirectories(outputDirectory);
    List<Violation> allViolations = new ArrayList<>();
    List<SceneArtifact> artifacts = new ArrayList<>();
    for (Scene scene : scenes) {
      List<Violation> violations = validate(scene);
      allViolations.addAll(violations);
      String stem = safeFileName(scene.id());
      Path png = outputDirectory.resolve(stem + ".png");
      Path json = outputDirectory.resolve(stem + ".json");
      render(scene, violations, png, palette);
      Files.writeString(
          json, json(scene, violations, palette), StandardCharsets.UTF_8);
      artifacts.add(new SceneArtifact(scene, png.getFileName(), json.getFileName(), violations));
    }
    Path index = outputDirectory.resolve("index.html");
    Files.writeString(
        index, html(title + " · " + palette.id(), artifacts), StandardCharsets.UTF_8);
    return new Result(index.toAbsolutePath(), allViolations);
  }

  public static List<Violation> validate(Scene scene) {
    Map<String, Node> byId = new HashMap<>();
    List<Violation> violations = new ArrayList<>();
    Rect screen = new Rect(0, 0, scene.width(), scene.height());
    for (Node node : scene.nodes()) {
      if (byId.put(node.id(), node) != null) {
        violations.add(new Violation(
            scene.id(), node.id(), "", "duplicate node id", node.bounds()));
      }
    }
    for (Node node : scene.nodes()) {
      Rect visual = visualBounds(node);
      if (!screen.contains(visual)) {
        violations.add(new Violation(
            scene.id(), node.id(), "", "visual bounds exceed screen", visual));
      }
      if (!node.parentId().isBlank()) {
        Node parent = byId.get(node.parentId());
        if (parent == null) {
          violations.add(new Violation(
              scene.id(), node.id(), node.parentId(), "parent is missing", visual));
        } else if (!parent.bounds().contains(visual)) {
          violations.add(new Violation(
              scene.id(), node.id(), parent.id(), "visual bounds exceed parent", visual));
        }
      }
    }
    for (int left = 0; left < scene.nodes().size(); left++) {
      Node first = scene.nodes().get(left);
      if (!first.collisionChecked()) {
        continue;
      }
      Rect firstBounds = visualBounds(first);
      for (int right = left + 1; right < scene.nodes().size(); right++) {
        Node second = scene.nodes().get(right);
        if (!second.collisionChecked()) {
          continue;
        }
        Rect secondBounds = visualBounds(second);
        if (firstBounds.intersects(secondBounds)) {
          violations.add(new Violation(
              scene.id(), first.id(), second.id(),
              "visible elements overlap", union(firstBounds, secondBounds)));
        }
      }
    }
    return List.copyOf(violations);
  }

  private static Rect visualBounds(Node node) {
    if (node.kind() != Kind.TEXT) {
      return node.bounds();
    }
    int measured = textWidth(node.text());
    int width = node.clipText()
        ? Math.min(node.bounds().width(), measured)
        : measured;
    return new Rect(
        node.bounds().x(), node.bounds().y(), Math.max(1, width), 9);
  }

  private static int textWidth(String text) {
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setFont(FONT);
    FontMetrics metrics = graphics.getFontMetrics();
    int javaWidth = metrics.stringWidth(text);
    graphics.dispose();
    int conservative = 0;
    for (int offset = 0; offset < text.length();) {
      int codePoint = text.codePointAt(offset);
      conservative += codePoint <= 0x7F ? 6 : 9;
      offset += Character.charCount(codePoint);
    }
    return Math.max(javaWidth, conservative);
  }

  private static Rect union(Rect first, Rect second) {
    int x = Math.min(first.x(), second.x());
    int y = Math.min(first.y(), second.y());
    int right = Math.max(first.right(), second.right());
    int bottom = Math.max(first.bottom(), second.bottom());
    return new Rect(x, y, right - x, bottom - y);
  }

  private static void render(
      Scene scene, List<Violation> violations, Path output, Palette palette) throws IOException {
    BufferedImage image = new BufferedImage(
        scene.width() * RENDER_SCALE,
        scene.height() * RENDER_SCALE,
        BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphics.scale(RENDER_SCALE, RENDER_SCALE);
    graphics.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    graphics.setColor(new Color(27, 38, 35));
    graphics.fillRect(0, 0, scene.width(), scene.height());
    graphics.setFont(FONT);
    for (Node node : scene.nodes()) {
      paintNode(graphics, node, palette);
    }
    graphics.setStroke(new BasicStroke(2.0F));
    graphics.setColor(new Color(255, 55, 55));
    for (Violation violation : violations) {
      Rect bounds = violation.bounds();
      graphics.drawRect(bounds.x(), bounds.y(),
          Math.max(1, bounds.width()), Math.max(1, bounds.height()));
    }
    graphics.dispose();
    ImageIO.write(image, "png", output.toFile());
  }

  private static void paintNode(Graphics2D graphics, Node node, Palette palette) {
    Rect bounds = node.bounds();
    switch (node.kind()) {
      case PANEL -> {
        graphics.setColor(new Color(palette.panel(), true));
        graphics.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.setColor(new Color(palette.border(), true));
        graphics.drawRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
      }
      case REGION -> {
        graphics.setColor(new Color(palette.section(), true));
        graphics.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.setColor(new Color(palette.border(), true));
        graphics.drawRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
      }
      case BUTTON -> {
        graphics.setColor(new Color(node.glyph() == null ? palette.button() : node.glyph().face(), true));
        graphics.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.setColor(new Color(palette.border(), true));
        graphics.drawRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        if (node.glyph() == null) drawCentered(graphics, bounds, node.text(), new Color(palette.text(), true));
        else node.glyph().paint(graphics, bounds);
      }
      case FIELD -> {
        graphics.setColor(new Color(palette.input(), true));
        graphics.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.setColor(new Color(palette.border(), true));
        graphics.drawRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        drawClipped(graphics, bounds, node.text(), new Color(palette.text(), true), 4);
      }
      case TEXT -> drawClipped(
          graphics,
          node.clipText() ? bounds : visualBounds(node),
          node.text(),
          new Color(palette.text(), true),
          0);
      case SCROLLBAR -> {
        graphics.setColor(new Color(palette.track(), true));
        graphics.fillRect(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        graphics.setColor(new Color(palette.thumb(), true));
        graphics.fillRect(bounds.x(), bounds.y(), bounds.width(),
            Math.max(12, bounds.height() / 4));
      }
    }
  }

  private static void drawCentered(
      Graphics2D graphics, Rect bounds, String text, Color color) {
    FontMetrics metrics = graphics.getFontMetrics();
    String clipped = clip(text, Math.max(1, bounds.width() - 6), metrics);
    int x = bounds.x() + Math.max(2, (bounds.width() - metrics.stringWidth(clipped)) / 2);
    int y = bounds.y() + (bounds.height() - metrics.getHeight()) / 2 + metrics.getAscent();
    graphics.setColor(color);
    graphics.drawString(clipped, x, y);
  }

  private static void drawClipped(
      Graphics2D graphics, Rect bounds, String text, Color color, int inset) {
    FontMetrics metrics = graphics.getFontMetrics();
    String clipped = clip(text, Math.max(1, bounds.width() - inset * 2), metrics);
    graphics.setColor(color);
    graphics.drawString(clipped, bounds.x() + inset,
        bounds.y() + Math.min(bounds.height() - 1, metrics.getAscent()));
  }

  private static String clip(String text, int width, FontMetrics metrics) {
    if (metrics.stringWidth(text) <= width) {
      return text;
    }
    String suffix = "…";
    int end = text.length();
    while (end > 0
        && metrics.stringWidth(text.substring(0, end) + suffix) > width) {
      end = text.offsetByCodePoints(end, -1);
    }
    return text.substring(0, end) + suffix;
  }

  private static String json(Scene scene, List<Violation> violations, Palette palette) {
    StringBuilder json = new StringBuilder();
    json.append("{\n  \"id\": \"").append(escapeJson(scene.id()))
        .append("\",\n  \"title\": \"").append(escapeJson(scene.title()))
        .append("\",\n  \"logicalWidth\": ").append(scene.width())
        .append(",\n  \"logicalHeight\": ").append(scene.height())
        .append(",\n  \"guiScale\": ")
        .append(String.format(Locale.ROOT, "%.2f", scene.guiScale()))
        .append(",\n  \"sourcePixelWidth\": ")
        .append(Math.round(scene.width() * scene.guiScale()))
        .append(",\n  \"sourcePixelHeight\": ")
        .append(Math.round(scene.height() * scene.guiScale()))
        .append(",\n  \"palette\": \"").append(escapeJson(palette.id()))
        .append("\",\n  \"evidence\": \"OFFLINE_GEOMETRY_NOT_IN_GAME\"")
        .append(",\n  \"nodes\": [\n");
    for (int index = 0; index < scene.nodes().size(); index++) {
      Node node = scene.nodes().get(index);
      json.append("    {\"id\": \"").append(escapeJson(node.id()))
          .append("\", \"kind\": \"").append(node.kind())
          .append("\", \"x\": ").append(node.bounds().x())
          .append(", \"y\": ").append(node.bounds().y())
          .append(", \"width\": ").append(node.bounds().width())
          .append(", \"height\": ").append(node.bounds().height())
          .append(", \"parent\": \"").append(escapeJson(node.parentId()))
          .append("\", \"text\": \"").append(escapeJson(node.text()))
          .append("\"}");
      json.append(index + 1 < scene.nodes().size() ? ",\n" : "\n");
    }
    json.append("  ],\n  \"violations\": [\n");
    for (int index = 0; index < violations.size(); index++) {
      Violation violation = violations.get(index);
      json.append("    {\"node\": \"")
          .append(escapeJson(violation.nodeId()))
          .append("\", \"other\": \"")
          .append(escapeJson(violation.otherId()))
          .append("\", \"message\": \"")
          .append(escapeJson(violation.message())).append("\"}");
      json.append(index + 1 < violations.size() ? ",\n" : "\n");
    }
    return json.append("  ]\n}\n").toString();
  }

  private static String html(String title, List<SceneArtifact> artifacts) {
    StringBuilder html = new StringBuilder("<!doctype html><html lang=\"zh-CN\"><head>")
        .append("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">")
        .append("<title>").append(escapeHtml(title)).append("</title>")
        .append("<style>body{font-family:system-ui;background:#0c1118;color:#e8edf3;margin:24px}")
        .append("article{background:#141d28;border:1px solid #334154;margin:18px 0;padding:16px}")
        .append("img{max-width:100%;height:auto;border:1px solid #526279;background:#111}")
        .append(".pass{color:#78e08f}.fail{color:#ff6b6b}code{color:#70e3ff}</style></head><body>")
        .append("<h1>").append(escapeHtml(title)).append("</h1>")
        .append("<p>离线布局示意，非游戏截图；不证明字体、输入或实际 GUI scale。颜色取自指定主题，geometry-only 为中性示意色。红框表示越界或未获准的可见元素碰撞。</p>");
    for (SceneArtifact artifact : artifacts) {
      Scene scene = artifact.scene();
      html.append("<article><h2>").append(escapeHtml(scene.title())).append("</h2>")
          .append("<p><code>").append(scene.width()).append('×').append(scene.height())
          .append(" logical / GUI scale ").append(scene.guiScale()).append(" / ")
          .append(Math.round(scene.width() * scene.guiScale())).append('×')
          .append(Math.round(scene.height() * scene.guiScale())).append(" source px</code> · ")
          .append(artifact.violations().isEmpty()
              ? "<strong class=\"pass\">PASS</strong>"
              : "<strong class=\"fail\">FAIL</strong>")
          .append(" · <a href=\"").append(artifact.json()).append("\">geometry JSON</a></p>")
          .append("<img src=\"").append(artifact.png()).append("\" alt=\"")
          .append(escapeHtml(scene.title())).append("\">");
      if (!artifact.violations().isEmpty()) {
        html.append("<ul>");
        for (Violation violation : artifact.violations()) {
          html.append("<li>").append(escapeHtml(violation.nodeId()))
              .append(violation.otherId().isBlank() ? "" : " ↔ " + escapeHtml(violation.otherId()))
              .append(": ").append(escapeHtml(violation.message())).append("</li>");
        }
        html.append("</ul>");
      }
      html.append("</article>");
    }
    return html.append("</body></html>\n").toString();
  }

  private static String safeFileName(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
        .replace("\t", "\\t");
  }

  private static String escapeHtml(String value) {
    return value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private record SceneArtifact(
      Scene scene,
      Path png,
      Path json,
      List<Violation> violations) {}
}
