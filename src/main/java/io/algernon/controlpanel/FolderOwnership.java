package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.Locale;
import java.util.Set;

/**
 * Whether a folder is one only this user can write in (ADR-291 §8): a folder, and where the file system has POSIX
 * permissions, owned by the named user and not writable by its group or by others. The one check behind the launch
 * values' folder (ADR-292 §3) and the working directory's (ADR-295 §1); each states its own line.
 *
 * <p>For the working directory's folder alone, where the file system has no POSIX permissions, the owner is compared
 * too and who else may write there is not (ADR-330 §7): every folder of an ordinary NTFS drive hands that right down,
 * each working directory {@code vespera run} makes among them, so comparing it would refuse them all.
 */
final class FolderOwnership {

    private static final boolean POSIX = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

    private static final Set<PosixFilePermission> OTHERS_WRITING = Set.of(PosixFilePermission.GROUP_WRITE,
            PosixFilePermission.OTHERS_WRITE);

    private FolderOwnership() {
    }

    /** Whether {@code folder}, not followed if it is a link, is a folder only {@code userName} can write in. */
    static boolean isOwn(Path folder, String userName) {
        boolean own = Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS);
        if (own && POSIX) {
            try {
                PosixFileAttributes attributes = Files.readAttributes(folder, PosixFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
                own = attributes.owner().getName().equals(userName)
                        && attributes.permissions().stream().noneMatch(OTHERS_WRITING::contains);
            } catch (IOException e) {
                own = false;
            }
        }
        return own;
    }

    /**
     * Whether {@code folder}, not followed if it is a link, is a working directory this user may write in (ADR-330 §7):
     * as {@link #isOwn} says it on POSIX permissions; elsewhere, where the file system names an owner, one owned by
     * {@code userName} or by a group, as a process run as administrator leaves what it makes (ADR-292 §3); and where it
     * names none, a folder.
     */
    static boolean isOwnWorkingDirectory(Path folder, String userName) {
        if (POSIX || !Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) {
            return isOwn(folder, userName);
        }
        try {
            if (!Files.getFileStore(folder).supportsFileAttributeView("owner")) {
                return true;
            }
            UserPrincipal owner = Files.getOwner(folder, LinkOption.NOFOLLOW_LINKS);
            return owner instanceof GroupPrincipal || namesUser(owner.getName(), userName);
        } catch (IOException | UnsupportedOperationException cannotBeTold) {
            return false;
        }
    }

    /** Whether the owner's name, as the system spells it, is the user's: a name after a domain and a backslash is the user's own. */
    private static boolean namesUser(String owner, String userName) {
        String bare = owner.substring(owner.lastIndexOf('\\') + 1);
        return owner.equalsIgnoreCase(userName) || bare.toLowerCase(Locale.ROOT).equals(userName.toLowerCase(Locale.ROOT));
    }
}
