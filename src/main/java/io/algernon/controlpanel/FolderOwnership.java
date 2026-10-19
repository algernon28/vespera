package io.algernon.controlpanel;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

/**
 * Whether a folder is one only this user can write in (ADR-291 §8): a folder, and where the file system has POSIX
 * permissions, owned by the named user and not writable by its group or by others. The one check behind the launch
 * values' folder (ADR-292 §3) and the working directory's (ADR-295 §1); each states its own line.
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
}
