package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.Citation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The writing's Markdown read once into blocks from a fixed subset, with no dependency (ADR-274 §1.3, ADR-046), and
 * written by one of two emitters: {@link ProseHtml} for the HTML tree and {@link ProseStorage} for the Confluence
 * rendering (#620). Both read the same blocks, so the two renderings carry the same paragraphs, lists, headings and
 * citations.
 *
 * <p><b>Escaping comes first, always.</b> The whole text is escaped by {@link Html#escape} before any markup is
 * added, and every construct is recognised on the escaped text, whose delimiters the rule leaves as they are. The
 * blocks hold escaped text with {@code strong}, {@code em} and {@code code} already in it, the same in either
 * rendering; what an emitter adds is the line break and the citation, each its own.
 *
 * <p><b>Everything outside the subset is shown as escaped text and never read</b>: a table, a link, an image, raw
 * HTML, a block quote, a rule, a fenced block, four {@code #} and more.
 */
final class Prose {

    private static final Pattern HEADING = Pattern.compile("^(#{1,3}) (.+)$");
    private static final Pattern BULLET = Pattern.compile("^[-*] (.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\d+\\. (.+)$");
    private static final Pattern BLANK_LINE_BETWEEN_BLOCKS = Pattern.compile("\\n\\s*\\n");

    private static final Pattern CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(?=\\S)(.+?)(?<=\\S)\\*\\*");
    private static final Pattern ITALIC_STAR = Pattern.compile(
            "(?<![\\p{L}\\p{N}*])\\*(?=[^\\s*])(.+?)(?<=[^\\s*])\\*(?![\\p{L}\\p{N}*])");
    private static final Pattern ITALIC_UNDERSCORE = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])_(?=[^\\s_])(.+?)(?<=[^\\s_])_(?![\\p{L}\\p{N}_])");

    /** What stands for a code span while emphasis is read; never in the text, since the text has none (see {@link #of}). */
    private static final char HELD = '\u0000';

    private Prose() {}

    /** What an emitter writes of its own: the break between two lines of one block, and a citation. */
    interface Emitter {

        /** The break between two lines of a paragraph or of a list's item. */
        String lineBreak();

        /** The citation {@code [digits]}, the digits being those the text matched. */
        String citation(String digits);
    }

    /** One block of the writing. */
    sealed interface Block {}

    /** A heading, {@code level} from 3 to 5 (ADR-274 §1.3). */
    record Heading(int level, String line) implements Block {}

    /** A paragraph's lines. */
    record Paragraph(List<String> lines) implements Block {}

    /** A list, numbered or not, each item its lines. */
    record Items(boolean numbered, List<List<String>> items) implements Block {}

    /** {@code markdown} as its blocks, each line escaped and carrying its emphasis and code, its citations as written. */
    static List<Block> of(String markdown) {
        String escaped = Html.escape(markdown.replace('\u0000', '�').replace("\r\n", "\n").replace('\r', '\n'));
        List<Block> blocks = new ArrayList<>();
        for (String block : BLANK_LINE_BETWEEN_BLOCKS.split(escaped)) {
            blockInto(block, blocks);
        }
        return List.copyOf(blocks);
    }

    /** {@code markdown}'s blocks written by {@code emitter}, one element to a line, or the empty text for none. */
    static String emit(String markdown, Emitter emitter) {
        List<String> elements = new ArrayList<>();
        for (Block block : of(markdown)) {
            elements.add(switch (block) {
                case Heading heading -> "<h" + heading.level() + ">" + line(heading.line(), emitter) + "</h"
                        + heading.level() + ">";
                case Paragraph paragraph -> "<p>" + lines(paragraph.lines(), emitter) + "</p>";
                case Items list -> {
                    String tag = list.numbered() ? "ol" : "ul";
                    StringBuilder items = new StringBuilder("<" + tag + ">");
                    list.items().forEach(item -> items.append("\n<li>").append(lines(item, emitter)).append("</li>"));
                    yield items.append("\n</").append(tag).append(">").toString();
                }
            });
        }
        return String.join("\n", elements);
    }

    private static String lines(List<String> lines, Emitter emitter) {
        List<String> written = new ArrayList<>();
        lines.forEach(line -> written.add(line(line, emitter)));
        return String.join(emitter.lineBreak(), written);
    }

    private static String line(String line, Emitter emitter) {
        return Citation.AS_WRITTEN.matcher(line).replaceAll(match -> Matcher.quoteReplacement(emitter.citation(match.group(1))));
    }

    private static void blockInto(String block, List<Block> blocks) {
        List<String> paragraph = new ArrayList<>();
        List<List<String>> items = new ArrayList<>();
        Boolean numberedList = null;
        for (String raw : block.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            Matcher bullet = BULLET.matcher(line);
            Matcher numbered = NUMBERED.matcher(line);
            if (heading.matches()) {
                closeParagraph(paragraph, blocks);
                numberedList = closeList(numberedList, items, blocks);
                blocks.add(new Heading(heading.group(1).length() + 2, inline(heading.group(2).strip())));
            } else if (bullet.matches() || numbered.matches()) {
                closeParagraph(paragraph, blocks);
                boolean isNumbered = !bullet.matches();
                if (numberedList != null && numberedList != isNumbered) {
                    numberedList = closeList(numberedList, items, blocks);
                }
                numberedList = isNumbered;
                List<String> item = new ArrayList<>();
                item.add(inline((bullet.matches() ? bullet : numbered).group(1).strip()));
                items.add(item);
            } else if (numberedList != null) {
                items.getLast().add(inline(line));
            } else {
                paragraph.add(inline(line));
            }
        }
        closeParagraph(paragraph, blocks);
        closeList(numberedList, items, blocks);
    }

    private static void closeParagraph(List<String> paragraph, List<Block> blocks) {
        if (!paragraph.isEmpty()) {
            blocks.add(new Paragraph(List.copyOf(paragraph)));
            paragraph.clear();
        }
    }

    /** Adds the list being held, if any, and answers that none is held. */
    private static Boolean closeList(Boolean numbered, List<List<String>> items, List<Block> blocks) {
        if (numbered != null) {
            blocks.add(new Items(numbered, items.stream().map(List::copyOf).toList()));
            items.clear();
        }
        return null;
    }

    /**
     * One escaped line with its code, bold and italic. A code span is held aside while emphasis is read, so nothing in
     * it is, and put back before the citations are, so a citation in it still links.
     */
    private static String inline(String escapedLine) {
        List<String> code = new ArrayList<>();
        Matcher spans = CODE.matcher(escapedLine);
        StringBuilder held = new StringBuilder();
        while (spans.find()) {
            spans.appendReplacement(held, Matcher.quoteReplacement(HELD + String.valueOf(code.size()) + HELD));
            code.add(spans.group(1));
        }
        spans.appendTail(held);
        String text = BOLD.matcher(held).replaceAll("<strong>$1</strong>");
        text = ITALIC_STAR.matcher(text).replaceAll("<em>$1</em>");
        text = ITALIC_UNDERSCORE.matcher(text).replaceAll("<em>$1</em>");
        for (int at = 0; at < code.size(); at++) {
            text = text.replace(HELD + String.valueOf(at) + HELD, "<code>" + code.get(at) + "</code>");
        }
        return text;
    }
}
