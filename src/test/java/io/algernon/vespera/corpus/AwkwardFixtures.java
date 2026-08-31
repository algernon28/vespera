package io.algernon.vespera.corpus;

import static org.junit.jupiter.api.Assumptions.abort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * The fixture that cannot be built by writing a file: a directory the walk may not read.
 *
 * <p>Built in the test rather than checked in, like every other fixture here (ADR-063) — an ACL is
 * exactly the thing a repository cannot carry anyway.
 *
 * <p>It aborts the calling test if this environment will not produce it. That is a capability check
 * and not an operating-system check: whether an ACL can be set depends on the volume and on who is
 * running the build, not only on the platform, and a test that quietly passes because it silently
 * built nothing is worse than one that says it was skipped.
 */
final class AwkwardFixtures {

    private AwkwardFixtures() {}

    /**
     * A directory the current user is denied read access to, so the walk meets it and cannot list it.
     *
     * <p>The deny entry goes first, which is how Windows evaluates an ACL: entries are read in order
     * and the first match wins, so a deny appended after the inherited allow would never be reached.
     *
     * @return the denied directory, holding one file that must therefore never be reached
     */
    static Path unreadableDirectory(Path parent) throws IOException {
        Path denied = Files.createDirectories(parent.resolve("denied"));
        Files.writeString(denied.resolve("unreachable.txt"), "behind a denied ACL");

        AclFileAttributeView acl = Files.getFileAttributeView(denied, AclFileAttributeView.class);
        if (acl == null) {
            abort("this filesystem has no ACL view, so a directory cannot be made unreadable");
        }

        UserPrincipal me = Files.getOwner(denied);
        AclEntry denyReading = AclEntry.newBuilder()
                .setType(AclEntryType.DENY)
                .setPrincipal(me)
                .setPermissions(EnumSet.of(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.LIST_DIRECTORY,
                        AclEntryPermission.READ_ATTRIBUTES))
                .build();

        List<AclEntry> entries = new ArrayList<>(acl.getAcl());
        entries.addFirst(denyReading);
        try {
            acl.setAcl(entries);
        } catch (IOException | SecurityException e) {
            abort("this environment will not let the test deny itself access: " + e.getMessage());
        }

        if (Files.isReadable(denied)) {
            abort("the deny entry did not take effect on this volume, so the directory is still readable");
        }
        return denied;
    }

    /**
     * Restores access to a directory {@link #unreadableDirectory} denied, so the temporary directory
     * can be deleted afterwards.
     */
    static void restoreAccess(Path denied) throws IOException {
        AclFileAttributeView acl = Files.getFileAttributeView(denied, AclFileAttributeView.class);
        if (acl == null) {
            return;
        }
        List<AclEntry> withoutDenials =
                acl.getAcl().stream().filter(entry -> entry.type() != AclEntryType.DENY).toList();
        acl.setAcl(withoutDenials);
    }
}
