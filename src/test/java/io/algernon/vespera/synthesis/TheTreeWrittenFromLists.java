package io.algernon.vespera.synthesis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every file of the tree {@code Deliverable.writeTo} wrote from {@link ThreePartitions}'s lists at
 * {@code 5b0cc20}, the commit before ADR-223 was built, by its relative path, each as the text it held.
 *
 * <p>Captured by a throwaway program and not written by hand: the working directory was {@code
 * <base>/work} and the corpus root {@code <base>/archive}, and the corpus root's own text, which differs
 * on every machine, stands here as {@link ThreePartitions#CORPUS_ROOT_TOKEN}. A test that compares
 * against it lays its directories out the same way and puts its own corpus root back.
 */
final class TheTreeWrittenFromLists {

    private TheTreeWrittenFromLists() {}

    /** The files by relative path, with {@code /} between segments, in path order. */
    static Map<String, String> files() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(
                "1-beta-co-2019/1-fire-doors-inspection.md",
                "# Fire doors \\& their inspection\n"
                + "\n"
                + "The doors were inspected twice [1](#document-1). The second inspection found a fault [2](#document-2)[1](#document-1).\n"
                + "\n"
                + "Written from the first 2 of the 3 documents in this group.\n"
                + "\n"
                + "## The documents in this group\n"
                + "\n"
                + "1. <a id=\"document-1\"></a>[reports/doors b.docx](../../../../archive/reports/doors%20b.docx)\n"
                + "\n"
                + "2. <a id=\"document-2\"></a>[reports/doors a.docx](../../../../archive/reports/doors%20a.docx)\n"
                + "\n"
                + "3. <a id=\"document-3\"></a>[reports/doors \\[final\\].docx](../../../../archive/reports/doors%20%5Bfinal%5D.docx)\n"
                + "\n");
        files.put(
                "1-beta-co-2019/2-smoke-dampers.md",
                "# Smoke dampers\n"
                + "\n"
                + "*Nothing was written over this group: the writing model's answer did not come back in the shape it was asked for, so it was not used.*\n"
                + "\n"
                + "## The documents in this group\n"
                + "\n"
                + "1. <a id=\"document-1\"></a>[reports/dampers \"old\".pdf](../../../../archive/reports/dampers%20%22old%22.pdf)\n"
                + "\n"
                + "2. <a id=\"document-2\"></a>[reports/dampers.pdf](../../../../archive/reports/dampers.pdf)\n"
                + "\n");
        files.put(
                "2-alpha/1-stairwells.md",
                "# Stairwells\n"
                + "\n"
                + "*Nothing was written over this group: none of its documents could be read and sent to the writing model.*\n"
                + "\n"
                + "## The documents in this group\n"
                + "\n"
                + "1. <a id=\"document-1\"></a>[reports/stairs, north.pdf](../../../../archive/reports/stairs,%20north.pdf)\n"
                + "\n"
                + "2. <a id=\"document-2\"></a>[reports/stairs, south.pdf](../../../../archive/reports/stairs,%20south.pdf)\n"
                + "\n");
        files.put(
                "2-alpha/2-a-note-on-risers.md",
                "# Risers\n"
                + "\n"
                + "[1](#document-1) One note covers the risers.\n"
                + "\n"
                + "## The documents in this group\n"
                + "\n"
                + "1. <a id=\"document-1\"></a>[notes/risers.txt](../../../../archive/notes/risers.txt)\n"
                + "\n");
        files.put(
                "3-gamma/1-sprinklers.md",
                "# Sprinklers\n"
                + "\n"
                + "*Nothing has been written over this group yet: writing stopped before it reached this group. Running the same command again carries the writing on.*\n"
                + "\n"
                + "## The documents in this group\n"
                + "\n"
                + "1. <a id=\"document-1\"></a>[reports/sprinklers-1.pdf](../../../../archive/reports/sprinklers-1.pdf)\n"
                + "\n"
                + "2. <a id=\"document-2\"></a>[reports/sprinklers-2.pdf](../../../../archive/reports/sprinklers-2.pdf)\n"
                + "\n");
        files.put(
                "3-gamma/2-hose-reels.md",
                "# Hose reels\n"
                + "\n"
                + "*nothing was written over this group*\n"
                + "\n"
                + "## The documents in this group\n"
                + "\n"
                + "1. <a id=\"document-1\"></a>[notes/hose reels.txt](../../../../archive/notes/hose%20reels.txt)\n"
                + "\n");
        files.put(
                "documents.csv",
                "occurrence_id,path,content_hash,winning_seed,relevance_score,seed_partition,cluster,partition_order,cluster_order\n"
                + "10,reports/doors a.docx,key10,2,0.5,seeds/Beta & Co [2019].pdf,0,1,1\n"
                + "11,\"reports/stairs, north.pdf\",key11,1,0.7,seeds/alpha.docx,0,2,1\n"
                + "12,reports/sprinklers-1.pdf,key12,3,0.4,seeds/gamma.docx,1,3,1\n"
                + "13,reports/doors [final].docx,key13,2,0.9,seeds/Beta & Co [2019].pdf,0,1,1\n"
                + "14,reports/dampers.pdf,key14,2,0.6,seeds/Beta & Co [2019].pdf,1,1,2\n"
                + "15,notes/risers.txt,key15,1,0.8,seeds/alpha.docx,2,2,2\n"
                + "16,notes/hose reels.txt,key16,3,0.3,seeds/gamma.docx,0,3,2\n"
                + "17,reports/doors b.docx,key17,2,0.5,seeds/Beta & Co [2019].pdf,0,1,1\n"
                + "18,\"reports/stairs, south.pdf\",key18,1,0.7,seeds/alpha.docx,0,2,1\n"
                + "19,reports/sprinklers-2.pdf,key19,3,0.4,seeds/gamma.docx,1,3,1\n"
                + "20,\"reports/dampers \"\"old\"\".pdf\",key20,2,0.65,seeds/Beta & Co [2019].pdf,1,1,2\n");
        files.put(
                "index.md",
                "# Deliverable f00df00df00df00df00df00df00df00df00df00df00df00df00df00df00df00d\n"
                + "\n"
                + "- Run: f00df00df00df00df00df00df00df00df00df00df00df00df00df00df00df00d\n"
                + "- Walk: 7\n"
                + "- Archive root: {CORPUS_ROOT}\n"
                + "- seedFolder: seeds\n"
                + "- relevanceFloor: \n"
                + "\n"
                + "The path column of documents.csv names each document beneath the archive root recorded above, and the links inside a group's page resolve against it too. The seed_partition column is not one of those: it names a seed beneath the seed folder, which is a root of its own. Nothing here is a copy of the archive, so if the archive moves, everything resolved against that archive root dies until this tree is re-pointed at where it went.\n"
                + "\n"
                + "## seeds/Beta \\& Co \\[2019\\].pdf\n"
                + "\n"
                + "| Group | Documents | Written up as |\n"
                + "|---|---|---|\n"
                + "| [Fire doors \\| inspection](1-beta-co-2019/1-fire-doors-inspection.md) | 3 | Fire doors \\& their inspection |\n"
                + "| Smoke dampers | 2 | *nothing was written over this group* |\n"
                + "\n"
                + "## seeds/alpha.docx\n"
                + "\n"
                + "| Group | Documents | Written up as |\n"
                + "|---|---|---|\n"
                + "| Stairwells | 2 | *nothing was written over this group* |\n"
                + "| [A note on \\[risers\\]](2-alpha/2-a-note-on-risers.md) | 1 | Risers |\n"
                + "\n"
                + "## seeds/gamma.docx\n"
                + "\n"
                + "| Group | Documents | Written up as |\n"
                + "|---|---|---|\n"
                + "| Sprinklers | 2 | *nothing was written over this group* |\n"
                + "| Hose reels | 1 | *nothing was written over this group* |\n");
        return files;
    }
}
