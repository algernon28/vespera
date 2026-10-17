package io.algernon.vespera.deliverable;

import io.algernon.vespera.synthesis.Citation;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The writing's Markdown converted to HTML from a fixed subset, with no dependency (ADR-274 §1.3, ADR-046).
 *
 * <p><b>Escaping comes first, always.</b> The whole text is escaped by {@link Html#escape} before any markup is
 * added, and every construct is recognised on the escaped text, whose delimiters the rule leaves as they are. So the
 * only tags in the output are the ones written here, {@code p br h3 h4 h5 ul ol li strong em code a}, and the only
 * attribute is a citation's {@code href="#document-n"}, made of digits the text matched.
 *
 * <p><b>Everything outside the subset is shown as escaped text and never read</b>: a table, a link, an image, raw
 * HTML, a block quote, a rule, a fenced block, four {@code #} and more.
 */
final class ProseHtml {

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

    private ProseHtml() {}

    /** {@code markdown} as HTML, one element to a line, or the empty text where it holds nothing. */
    static String of(String markdown) {
        String escaped = Html.escape(markdown.replace('\u0000', '�').replace("\r\n", "\n").replace('\r', '\n'));
        List<String> elements = new ArrayList<>();
        for (String block : BLANK_LINE_BETWEEN_BLOCKS.split(escaped)) {
            blockInto(block, elements);
        }
        return String.join("\n", elements);
    }

    private static void blockInto(String block, List<String> elements) {
        List<String> paragraph = new ArrayList<>();
        List<String> items = new ArrayList<>();
        String listTag = null;
        for (String raw : block.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher heading = HEADING.matcher(line);
            Matcher bullet = BULLET.matcher(line);
            Matcher numbered = NUMBERED.matcher(line);
            if (heading.matches()) {
                closeParagraph(paragraph, elements);
                listTag = closeList(listTag, items, elements);
                int level = heading.group(1).length() + 2;
                elements.add("<h" + level + ">" + inline(heading.group(2).strip()) + "</h" + level + ">");
            } else if (bullet.matches() || numbered.matches()) {
                closeParagraph(paragraph, elements);
                String tag = bullet.matches() ? "ul" : "ol";
                if (listTag != null && !listTag.equals(tag)) {
                    listTag = closeList(listTag, items, elements);
                }
                listTag = tag;
                items.add(inline((bullet.matches() ? bullet : numbered).group(1).strip()));
            } else if (listTag != null) {
                items.set(items.size() - 1, items.getLast() + "<br>" + inline(line));
            } else {
                paragraph.add(inline(line));
            }
        }
        closeParagraph(paragraph, elements);
        closeList(listTag, items, elements);
    }

    private static void closeParagraph(List<String> paragraph, List<String> elements) {
        if (!paragraph.isEmpty()) {
            elements.add("<p>" + String.join("<br>", paragraph) + "</p>");
            paragraph.clear();
        }
    }

    /** Writes the list being held, if any, and answers that none is held. */
    private static String closeList(String tag, List<String> items, List<String> elements) {
        if (tag != null) {
            StringBuilder list = new StringBuilder("<" + tag + ">");
            items.forEach(item -> list.append("\n<li>").append(item).append("</li>"));
            elements.add(list.append("\n</").append(tag).append(">").toString());
            items.clear();
        }
        return null;
    }

    /**
     * One escaped line with its code, bold, italic and citations. A code span is held aside while emphasis is read, so
     * nothing in it is, and put back before the citations are, so a citation in it still links.
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
        return Citation.AS_WRITTEN
                .matcher(text)
                .replaceAll(match -> "<a href=\"#" + ClusterPage.ANCHOR_PREFIX + match.group(1) + "\">[" + match.group(1)
                        + "]</a>");
    }
}
