package com.liucodex.doggiesnewvehicle.guide;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Read-only manual content. Vanilla owns opening, turning pages, copying and saving the written books. */
public final class DnvGuideBooks {
    public static final int WIDTH = 114, BODY_LINES = 11, INDEX_LINES = 10;
    public record Book(String title, List<Component> pages, Map<String,Integer> chapters) { }
    private record Entry(String id, String title, List<Component> lines) { }
    private record Atom(String text, boolean key) { }
    private static final Map<String,Book> BOOKS = Map.of("zh_cn", load("zh_cn"), "en_us", load("en_us"));
    private DnvGuideBooks() { }
    public static Book book(String language) {
        var book = BOOKS.get(language);
        if (book == null) throw new IllegalArgumentException("Unknown DNV guide language: " + language);
        return book;
    }
    private static Book load(String language) {
        String path = "/assets/doggiesnewvehicle/guides/" + language + ".json";
        var stream = DnvGuideBooks.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("Missing DNV guide " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var json = JsonParser.parseReader(reader).getAsJsonObject();
            var entries = new ArrayList<Entry>();
            for (var value : json.getAsJsonArray("entries")) {
                var entry = value.getAsJsonObject(); var lines = new ArrayList<Component>();
                String title = entry.get("title").getAsString();
                if (width(title) > WIDTH) throw new IllegalArgumentException("Guide heading too wide: " + title);
                for (var paragraph : entry.getAsJsonArray("text")) {
                    if (!lines.isEmpty()) lines.add(Component.empty());
                    lines.addAll(wrap(paragraph.getAsString()));
                }
                entries.add(new Entry(entry.get("id").getAsString(), title, List.copyOf(lines)));
            }
            int indexPages = (entries.size() + INDEX_LINES - 1) / INDEX_LINES;
            int next = indexPages + 1; var chapters = new LinkedHashMap<String,Integer>();
            for (var entry : entries) {
                if (chapters.put(entry.id(), next) != null) throw new IllegalArgumentException("Duplicate guide entry");
                next += (entry.lines().size() + BODY_LINES - 1) / BODY_LINES;
            }
            if (next > 101) throw new IllegalArgumentException("Vanilla guide exceeds 100 pages: " + language + " / " + (next-1));
            var pages = new ArrayList<Component>();
            String contents = json.get("contents").getAsString(), back = json.get("back").getAsString();
            for (int start = 0; start < entries.size(); start += INDEX_LINES) {
                MutableComponent page = Component.literal(contents + "\n\n");
                for (int i = start; i < Math.min(entries.size(), start + INDEX_LINES); i++) {
                    var entry = entries.get(i);
                    page.append(link(entry.title(), chapters.get(entry.id()))).append("\n");
                }
                pages.add(page);
            }
            for (int e = 0; e < entries.size(); e++) {
                var entry = entries.get(e);
                for (int start = 0; start < entry.lines().size(); start += BODY_LINES) {
                    MutableComponent page = Component.literal(entry.title() + "\n\n");
                    for (int i = start; i < Math.min(entry.lines().size(), start + BODY_LINES); i++)
                        page.append(entry.lines().get(i).copy()).append("\n");
                    page.append(link(back, e / INDEX_LINES + 1));
                    pages.add(page);
                }
            }
            return new Book(json.get("title").getAsString(), List.copyOf(pages), java.util.Collections.unmodifiableMap(chapters));
        } catch (java.io.IOException ex) { throw new IllegalStateException("Cannot read " + path, ex); }
    }
    private static Component link(String text, int page) {
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.DARK_BLUE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.CHANGE_PAGE, Integer.toString(page))));
    }
    /** Conservative vanilla glyph advances; authored words and CJK wrap without cutting keybind components. */
    public static int width(String text) {
        return text.codePoints().map(c -> c == ' ' ? 4 : c == '@' || c == '~' ? 7 : c < 128 ? 6 : 9).sum();
    }
    private static List<Component> wrap(String text) {
        var atoms = new ArrayList<Atom>();
        var matcher = java.util.regex.Pattern.compile("\\[\\[([^]]+)]]|[A-Za-z0-9]+|.", java.util.regex.Pattern.DOTALL).matcher(text);
        while (matcher.find()) {
            if (matcher.group(1) != null) atoms.add(new Atom(matcher.group(1), true));
            else if (width(matcher.group()) > WIDTH) matcher.group().codePoints().forEach(c -> atoms.add(new Atom(new String(Character.toChars(c)), false)));
            else atoms.add(new Atom(matcher.group(), false));
        }
        var lines = new ArrayList<Component>(); MutableComponent line = Component.empty(); int used = 0;
        for (var atom : atoms) {
            int w = atom.key() ? WIDTH : width(atom.text());
            if (atom.text().equals("\n") || used + w > WIDTH) {
                lines.add(line); line = Component.empty(); used = 0;
                if (atom.text().equals("\n") || atom.text().equals(" ")) continue;
            }
            if (used == 0 && atom.text().equals(" ")) continue;
            line.append(atom.key() ? Component.keybind(atom.text()) : Component.literal(atom.text())); used += w;
        }
        if (used > 0) lines.add(line);
        return lines;
    }
}
