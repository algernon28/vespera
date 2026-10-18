package io.algernon.vespera;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the {@code <div>}s of a page as written and the hover text each carries (ADR-275): every opening tag of a
 * {@code <div>}, the text of its {@code title}, and whether the tag is written as a double-quoted attribute list
 * whose values hold nothing that would end the attribute or the tag.
 */
public final class HoverTexts {

    /** An opening tag of a {@code <div>}, up to the first {@code >}: a value holding one is cut short and fails {@link #WELL_FORMED}. */
    private static final Pattern DIV = Pattern.compile("<div\\b[^>]*>");

    /** A {@code <div>} whose every attribute is a lower-case name and a double-quoted value with no {@code <}, {@code >} or {@code "}. */
    private static final Pattern WELL_FORMED = Pattern.compile("<div(?: [a-z][a-z-]*=\"[^\"<>]*\")*>");

    private static final Pattern TITLE = Pattern.compile(" title=\"([^\"]*)\"");

    /** An ampersand that opens no entity: a raw {@code &} in an attribute's value. */
    private static final Pattern A_BARE_AMPERSAND = Pattern.compile("&(?!(?:[a-zA-Z]+|#[0-9]+|#x[0-9a-fA-F]+);)");

    private HoverTexts() {}

    /** Every opening tag of a {@code <div>} on {@code page}, in the order written. */
    public static List<String> divsOf(String page) {
        List<String> tags = new ArrayList<>();
        Matcher div = DIV.matcher(page);
        while (div.find()) {
            tags.add(div.group());
        }
        return tags;
    }

    /** The value of {@code tag}'s {@code title} as written, or {@code null} where it carries none. */
    public static String titleOf(String tag) {
        Matcher title = TITLE.matcher(tag);
        return title.find() ? title.group(1) : null;
    }

    /** The value of the attribute {@code name} of {@code tag} as written, or {@code null} where it carries none. */
    public static String attributeOf(String tag, String name) {
        Matcher attribute = Pattern.compile(" " + Pattern.quote(name) + "=\"([^\"]*)\"").matcher(tag);
        return attribute.find() ? attribute.group(1) : null;
    }

    /**
     * Whether {@code tag} is a list of double-quoted attributes whose values hold no {@code <}, {@code >} or
     * {@code "}, and no {@code &} that opens no entity: what a value escaped for its attribute leaves.
     */
    public static boolean escapedForItsAttributes(String tag) {
        return WELL_FORMED.matcher(tag).matches() && !A_BARE_AMPERSAND.matcher(tag).find();
    }
}
