package io.algernon.vespera.pipeline;

/**
 * Stage 6a's page as {@code ArrangementReport.render} wrote it whole at {@code 5b0cc20}, the commit before
 * ADR-223 was built, for {@link ThreePartitionsOnThePage}'s partitions and for none.
 *
 * <p>Captured by a throwaway program and not written by hand.
 */
final class TheArrangementPageRenderedWhole {

    private TheArrangementPageRenderedWhole() {}

    /** The page for the three partitions. */
    static String threePartitions() {
        return
                "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<title>How the documents are arranged</title>\n"
                + "<style>\n"
                + "body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n"
                + "table { border-collapse: collapse; margin-bottom: 1.5em; width: 100%; }\n"
                + "td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }\n"
                + "td.count { text-align: right; }\n"
                + "code.name { font-size: 1.2em; padding: 0.2em 0.4em; background: #eee; }\n"
                + ".bar { background: #4a90d9; height: 1em; }\n"
                + "blockquote { color: #444; font-style: italic; margin: 0.4em 0 0 1em; }\n"
                + "li { margin-bottom: 0.6em; }\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<h1>How the documents are arranged</h1>\n"
                + "<p>Every document that survived is here, under the exemplar it was matched to and in the group it formed with the documents nearest it. Nothing has been removed, merged or renamed to produce this page, and nothing on it was written for you \u2014 every name below was taken from a document you can open.</p>\n"
                + "<p>The documents themselves are still in <code>C:\\archive &lt;2019&gt; &amp; after</code>. They have not been moved or copied.</p>\n"
                + "<h2>What you are being asked</h2>\n"
                + "<p><b>Is this arrangement worth writing over?</b> The next step reads each group in full and writes a document about it, which is the slowest and most expensive thing this tool does. It will not start until you say this arrangement is the one you want.</p>\n"
                + "<p>To say so, put this into <code>profile.yaml</code> under <code>arrangementApproved</code>:</p>\n"
                + "<p><code class=\"name\">f00df00df00d</code></p>\n"
                + "<p>That name is this arrangement and no other. If the documents are grouped again \u2014 because you changed a setting, or added to the archive \u2014 this page gets a new name and you are asked again. That is deliberate: an approval that never expired would mean shipping an arrangement nobody looked at.</p>\n"
                + "<p>Write down what you actually checked in <code>provenance</code> beside it. Nothing verifies it; it is there so that whoever reads this archive later knows what the approval was worth.</p>\n"
                + "<h2>seeds/Beta &amp; Co [2019].pdf</h2>\n"
                + "<p>5 document(s) in 2 group(s).</p>\n"
                + "<table>\n"
                + "<tr><th>Group</th><th>Documents</th><th>Named after</th></tr>\n"
                + "<tr><td>Fire doors | inspection</td><td class=\"count\">3</td><td><a href=\"file:///C:/archive/reports/doors%20%5Bfinal%5D.docx\">reports/doors [final].docx</a></td></tr>\n"
                + "<tr><td>Smoke dampers</td><td class=\"count\">2</td><td><a href=\"file:///C:/archive/reports/dampers.pdf\">reports/dampers \"old\".pdf</a></td></tr>\n"
                + "</table>\n"
                + "<h2>seeds/alpha.docx</h2>\n"
                + "<p>3 document(s) in 2 group(s).</p>\n"
                + "<table>\n"
                + "<tr><th>Group</th><th>Documents</th><th>Named after</th></tr>\n"
                + "<tr><td>Stairwells</td><td class=\"count\">2</td><td><a href=\"file:///C:/archive/reports/stairs.pdf\">reports/stairs, north.pdf</a></td></tr>\n"
                + "<tr><td>A note on &lt;risers&gt;</td><td class=\"count\">1</td><td><a href=\"file:///C:/archive/notes/risers.txt\">notes/risers.txt</a></td></tr>\n"
                + "</table>\n"
                + "<h2>seeds/gamma.docx</h2>\n"
                + "<p>1 document(s) in 1 group(s).</p>\n"
                + "<table>\n"
                + "<tr><th>Group</th><th>Documents</th><th>Named after</th></tr>\n"
                + "<tr><td>Hose reels</td><td class=\"count\">1</td><td><a href=\"file:///C:/archive/notes/hose%20reels.txt\">notes/hose reels.txt</a></td></tr>\n"
                + "</table>\n"
                + "<h2>How to read this</h2>\n"
                + "<p>Exemplars are in order of how much of the archive sits under them, largest first, so the substantial part of what you have is at the top. Within each one, groups holding several documents come before groups holding one, and within those two tiers the groups closest to the exemplar come first.</p>\n"
                + "<p><em>Named after</em> is the document each group's name was taken from \u2014 the one closest to the exemplar, and a link straight to where it already sits. Open a few. If a name does not describe the group it heads, that is what this page is for, and grouping the documents again with different settings is cheap compared with what comes next.</p>\n"
                + "<p>A group holding one document is a real outcome rather than a mistake: it means that document was collected on its own merits and not because it belongs with the others.</p>\n"
                + "</body>\n"
                + "</html>\n";
    }

    /** The page for an arrangement of no partition. */
    static String noPartition() {
        return
                "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<title>How the documents are arranged</title>\n"
                + "<style>\n"
                + "body { font-family: sans-serif; margin: 2em; max-width: 900px; }\n"
                + "table { border-collapse: collapse; margin-bottom: 1.5em; width: 100%; }\n"
                + "td, th { border: 1px solid #ccc; padding: 0.3em 0.8em; text-align: left; }\n"
                + "td.count { text-align: right; }\n"
                + "code.name { font-size: 1.2em; padding: 0.2em 0.4em; background: #eee; }\n"
                + ".bar { background: #4a90d9; height: 1em; }\n"
                + "blockquote { color: #444; font-style: italic; margin: 0.4em 0 0 1em; }\n"
                + "li { margin-bottom: 0.6em; }\n"
                + "</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<h1>How the documents are arranged</h1>\n"
                + "<p>Every document that survived is here, under the exemplar it was matched to and in the group it formed with the documents nearest it. Nothing has been removed, merged or renamed to produce this page, and nothing on it was written for you \u2014 every name below was taken from a document you can open.</p>\n"
                + "<p>The documents themselves are still in <code>C:\\archive &lt;2019&gt; &amp; after</code>. They have not been moved or copied.</p>\n"
                + "<h2>What you are being asked</h2>\n"
                + "<p><b>Is this arrangement worth writing over?</b> The next step reads each group in full and writes a document about it, which is the slowest and most expensive thing this tool does. It will not start until you say this arrangement is the one you want.</p>\n"
                + "<p>To say so, put this into <code>profile.yaml</code> under <code>arrangementApproved</code>:</p>\n"
                + "<p><code class=\"name\">f00df00df00d</code></p>\n"
                + "<p>That name is this arrangement and no other. If the documents are grouped again \u2014 because you changed a setting, or added to the archive \u2014 this page gets a new name and you are asked again. That is deliberate: an approval that never expired would mean shipping an arrangement nobody looked at.</p>\n"
                + "<p>Write down what you actually checked in <code>provenance</code> beside it. Nothing verifies it; it is there so that whoever reads this archive later knows what the approval was worth.</p>\n"
                + "<p>No document was matched to an exemplar, so there is nothing to arrange.</p>\n"
                + "</body>\n"
                + "</html>\n";
    }
}
