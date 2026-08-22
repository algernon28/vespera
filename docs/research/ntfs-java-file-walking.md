# How Java file walking actually behaves on NTFS

Research record for [issue #2](https://github.com/algernon28/vespera/issues/2). Facts only — no decisions.
Feeds the occurrence-identity ticket ([#4](https://github.com/algernon28/vespera/issues/4)) and the testing
ticket ([#18](https://github.com/algernon28/vespera/issues/18)).

## Scope and method

Every claim below is either (a) quoted from a primary source — the `java.nio.file` Javadoc, OpenJDK source, the
OpenJDK bug database, or Microsoft Learn — or (b) **measured** on the machine described below and labelled
`MEASURED`. Where I could not find a primary source, the claim is labelled `UNSOURCED` and says so.

Measurement environment:

| | |
|---|---|
| OS | Windows 11 Pro, build 10.0.26200 |
| JDK | OpenJDK 26.0.2+10-55 (matches `<java.version>26</java.version>` in `pom.xml`) |
| Volumes | C: and D:, both NTFS |
| `HKLM\SYSTEM\CurrentControlSet\Control\FileSystem\LongPathsEnabled` | `1` |
| Privilege | non-elevated; Developer Mode on (so `mklink /D` worked without admin) |
| Per-directory case sensitivity | **not** exercised — `fsutil file setCaseSensitiveInfo` needs elevation |

JDK source citations are to the local `lib/src.zip` of that JDK, which matches
[openjdk/jdk `master`](https://github.com/openjdk/jdk) at the lines quoted. Links go to the `master` copies.

---

## 1. Long paths

### What Windows specifies

[Maximum Path Length Limitation](https://learn.microsoft.com/en-us/windows/win32/fileio/maximum-file-path-limitation):

> In the Windows API (with some exceptions discussed in the following paragraphs), the maximum length for a path
> is **MAX_PATH**, which is defined as 260 characters.

> To specify an extended-length path, use the "\\?\" prefix. […] These prefixes are not used as part of the path
> itself. They indicate that the path should be passed to the system with minimal modification, which means that
> you cannot use forward slashes to represent path separators, or a period to represent the current directory, or
> double dots to represent the parent directory. Because you cannot use the "\\?\" prefix with a relative path,
> relative paths are always limited to a total of **MAX_PATH** characters.

> When using an API to create a directory, the specified path cannot be so long that you cannot append an 8.3
> file name (that is, the directory name cannot exceed **MAX_PATH** minus 12).

Opting out of `MAX_PATH` (Windows 10 1607+) needs **both** halves:

> To enable the new long path behavior per application, two conditions must be met. A registry value must be set,
> and the application manifest must include the `longPathAware` element.

> The registry value `HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Control\FileSystem LongPathsEnabled
> (Type: REG_DWORD)` must exist and be set to `1`. […] Understand that enabling this registry setting will only
> affect applications that have been modified to take advantage of the new feature.

The opt-out covers `FindFirstFileW`, `FindNextFileW`, `CreateFileW`, `GetFileAttributesExW`, `CopyFileExW`,
`MoveFileExW` and friends — i.e. everything directory walking needs.

Separately, the 255-character **per-component** limit is not affected by any of this
([Naming Files, Paths, and Namespaces](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file)
gives the component rules; [JDK-8366403](https://bugs.openjdk.org/browse/JDK-8366403), closed *Not an Issue*, is
exactly a user hitting the 255-char name limit with `LongPathsEnabled=1` and blaming the JDK).

### What the JDK does

The JDK does **not** rely on the registry key or the manifest. It applies the prefix itself, on every
`java.nio.file` call, in
[`WindowsPath`](https://github.com/openjdk/jdk/blob/master/src/java.base/windows/classes/sun/nio/fs/WindowsPath.java):

```java
// The maximum path that does not require long path prefix. On Windows
// the maximum path is 260 minus 1 (NUL) but for directories it is 260
// minus 12 minus 1 (to allow for the creation of a 8.3 file in the
// directory).
private static final int MAX_PATH = 247;

// Maximum extended-length path
private static final int MAX_LONG_PATH = 32000;
```

```java
String resolved = getAbsolutePath();
if (resolved.length() > MAX_PATH || !allowShortPath) {
    if (resolved.length() > MAX_LONG_PATH) {
        throw new WindowsException("Cannot access file with path exceeding "
            + MAX_LONG_PATH + " characters");
    }
    resolved = addPrefix(GetFullPathName(resolved));
}
```

```java
static String addPrefix(String path) {
    if (path.startsWith("\\\\")) {
        path = "\\\\?\\UNC" + path.substring(1, path.length());
    } else {
        path = "\\\\?\\" + path;
    }
    return path;
}
```

Three consequences:

1. **Callers never need `\\?\`.** `getPathForWin32Calls()` resolves the path to absolute first, so even a
   *relative* `Path` longer than `MAX_PATH` works — the Win32 relative-path limit quoted above does not apply to
   `java.nio.file`.
2. The prefix is an implementation detail; it never appears in `Path::toString`. Since JDK 22 `java.io.File`
   *strips* a prefix you supply
   ([JDK-8320371 release note](https://bugs.openjdk.org/browse/JDK-8320371),
   [JDK-8317555 CSR](https://bugs.openjdk.org/browse/JDK-8317555)):
   > This change has no impact to file access, the JDK will continue to use the long path prefix when accessing
   > files that need the prefix.
3. There is a **hard ceiling at 32,000 characters**, surfaced as an `IOException`.

The JDK launcher still does **not** declare `longPathAware`:
[JDK-8348664 "Enable long path support in manifest for java.exe and javaw.exe on Windows"](https://bugs.openjdk.org/browse/JDK-8348664)
is open (status *New*, filed 2025-01-23), and quotes the missing manifest section. So anything that reaches Win32
*without* going through `WindowsPath` — native libraries loaded into the JVM, `CreateProcess` — stays
`MAX_PATH`-limited regardless of the registry key. The visible symptom is
[JDK-8315405 "Can't start process in directory with very long path"](https://bugs.openjdk.org/browse/JDK-8315405)
(closed *External*): `ProcessBuilder` with a >260-char working directory fails with
`CreateProcess error=267, The directory name is invalid`.

### `MEASURED`

Building a tree with a 472-character leaf path on C: (NTFS):

| Call | Result |
|---|---|
| `Files.createDirectories`, `Files.writeString` | OK |
| `Files.exists` | `true` |
| `Path::toString` | no `\\?\` prefix |
| `Path::toRealPath` | OK, 472 chars |
| `Files.walk(base)` | OK, all 10 entries |
| `Files.newDirectoryStream` | OK |
| `java.io.File::exists`, `::length`, `::getCanonicalPath` | OK |
| `Files.exists` on a 32,105-char path | **`false`** — silently, no exception |
| `Files.readAttributes` on the same path | `java.io.IOException: Cannot access file with path exceeding 32000 characters` |

The `Files.exists` result is the trap: it is specified to answer `false` when the file "does not exist *or its
existence cannot be determined*", so an over-long path is indistinguishable from an absent file.

Note that this machine has `LongPathsEnabled=1`, so the measurement does not *isolate* the registry variable.
The attribution to the `\\?\` prefix rather than the registry key rests on the source above plus the fact that
`java.exe` has no `longPathAware` manifest (JDK-8348664) — per Microsoft, the registry key alone "will only
affect applications that have been modified to take advantage of the new feature". I could not A/B test the
registry key (no elevation).

### Windows vs Linux

Nothing in this section transfers. Linux enforces `PATH_MAX`/`NAME_MAX` in the kernel and returns
`ENAMETOOLONG`; there is no prefix, no registry key, no manifest, and no 32,000-char JDK ceiling. A long-path
test is **Windows-only**. (The 255-byte component limit does have a Linux analogue — the JDK-8366403 comments
record `NAME_MAX = 255` failing identically on macOS.)

---

## 2. Case

### What the specs say

`Path::equals`
([Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Path.html#equals(java.lang.Object)))
delegates the whole question to the provider:

> Whether or not two path are equal depends on the file system implementation. In some cases the paths are
> compared without regard to case, and others are case sensitive. This method does not access the file system and
> the file is not required to exist. Where required, the `isSameFile` method may be used to check if two paths
> locate the same file.

`WindowsPath` compares **case-insensitively, character by character, via `Character.toUpperCase`**:

```java
public int compareTo(Path obj) {
    ...
     if (c1 != c2) {
         c1 = Character.toUpperCase(c1);
         c2 = Character.toUpperCase(c2);
         if (c1 != c2) {
             return c1 - c2;
         }
     }
    ...
}

public boolean equals(Object obj) {
    return obj instanceof WindowsPath other && compareTo(other) == 0;
}

public int hashCode() {
    ...
        h = 31*h + Character.toUpperCase(path.charAt(i));
    ...
}
```

`toString()` returns the string as given, so `Path` is case-preserving and case-insensitive — the same contract
NTFS advertises. Microsoft
([Naming Files, Paths, and Namespaces](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file)):

> Do not assume case sensitivity. For example, consider the names OSCAR, Oscar, and oscar to be the same, even
> though some file systems (such as a POSIX-compliant file system) may consider them as different. Note that
> NTFS supports POSIX semantics for case sensitivity but this is not the default behavior.

> Volume designators (drive letters) are similarly case-insensitive.

### Per-directory case sensitivity

From [Case Sensitivity](https://learn.microsoft.com/en-us/windows/wsl/case-sensitivity):

- `fsutil.exe file setCaseSensitiveInfo <path> enable|disable`, queried with `queryCaseSensitiveInfo`.
- "Support for per-directory case sensitivity began in Windows 10, build 17107."
- Requires elevation plus write/create/delete permissions on the directory, and **the directory must be empty**;
  you cannot disable the flag on a directory that already holds case-colliding names.
- "When creating new directories, those directories will inherit the case sensitivity from its parent directory."
- NTFS local volumes only.
- Microsoft's own warning applies to the JDK as much as to any other Win32 program:
  > Some Windows applications, using the assumption that the file system is case insensitive, don't use the
  > correct case to refer to files. […] In directories marked as case sensitive, this means that these
  > applications can no longer access the files.

The JDK has no notion of this flag: `WindowsPath::equals` is unconditionally case-insensitive. So inside a
case-sensitive directory, `a.txt` and `A.txt` are two files that produce one `Path` value. **`UNSOURCED` /
untested:** I could not verify this on the machine (no elevation), and I found no JBS issue tracking it. Treat it
as a strong inference from the source, not a measurement.

### The folding tables do not agree — `MEASURED`

`Character.toUpperCase` is Unicode's simple uppercase mapping for the JDK's Unicode version. NTFS folds with its
own volume-resident uppercase table. They differ, and the difference is observable:

| pair | two distinct files on NTFS? | `Path.equals` | verdict |
|---|---|---|---|
| `i` / `I` (U+0069 / U+0049) | no (same file) | `true` | agree |
| `ı` / `I` (U+0131 / U+0049) | **yes** | **`true`** | **`Path.equals` wrong** |
| `K` / `K` (U+212A / U+004B) | yes | `false` | agree |
| `µ` / `μ` (U+00B5 / U+03BC) | **yes** | **`true`** | **`Path.equals` wrong** |
| `ſ` / `s` (U+017F / U+0073) | **yes** | **`true`** | **`Path.equals` wrong** |
| `ß` / `SS` | yes | `false` | agree |
| `Å` (U+0041 U+030A) / `Å` (U+00C5) | yes | `false` | agree |

Worse, `Files.isSameFile` inherits the bug, because
[`WindowsFileSystemProvider::isSameFile`](https://github.com/openjdk/jdk/blob/master/src/java.base/windows/classes/sun/nio/fs/WindowsFileSystemProvider.java)
short-circuits on path equality before touching the disk:

```java
public boolean isSameFile(Path obj1, Path obj2) throws IOException {
    WindowsPath file1 = WindowsPath.toWindowsPath(obj1);
    if (file1.equals(obj2))
        return true;
```

`MEASURED`: with `yı.txt` containing `AAA` and `yI.txt` containing `BBB` in the same directory,
`Files.isSameFile(a, b)` returns `true`, and `new HashSet<>(List.of(a, b)).size() == 1`. Two distinct files, one
identity. The pairs are exotic, but they are reachable from real-world text (Turkish, mathematical µ, historic
long s) and the failure is silent.

I found **no OpenJDK bug report** for either the folding-table mismatch or the `isSameFile` short-circuit
(`UNSOURCED` — searched JBS for `WindowsPath`/case/`isSameFile` combinations). The volume-resident uppercase
table (`$UpCase`) is also `UNSOURCED`: it is not documented on the
[Master File Table](https://learn.microsoft.com/en-us/windows/win32/fileio/master-file-table) page, so I cannot
cite Microsoft for *why* the tables diverge or for whether the table varies by the Windows version that
formatted the volume. The measurement above stands on its own regardless.

`MEASURED`, uncontroversially: creating `CaseFile.txt` and then looking it up as `CASEFILE.TXT` succeeds;
`Files.list` reports the on-disk casing `CaseFile.txt`; `toRealPath()` on the upper-case spelling returns
`CaseFile.txt`. So `toRealPath` is the reliable way to canonicalise casing.

### Windows vs Linux

`UnixPath` compares raw bytes
([source](https://github.com/openjdk/jdk/blob/master/src/java.base/unix/classes/sun/nio/fs/UnixPath.java)):

```java
public int compareTo(Path other) {
    return Arrays.compareUnsigned(path, ((UnixPath) other).path);
}
public boolean equals(Object ob) {
    return ob instanceof UnixPath p && compareTo(p) == 0;
}
```

So on Linux `Path::equals` is exactly case- and byte-sensitive, with no folding table at all. **Every assertion
in this section inverts on Linux.** Any test that pins down case behaviour — `Path.equals` on case variants, the
`isSameFile` short-circuit, the folding-table divergence, `toRealPath` canonicalisation — is Windows-only and
cannot run on a Linux CI runner.

---

## 3. Reparse points: junctions, symlinks, mount points

### What Windows says

[Reparse Points](https://learn.microsoft.com/en-us/windows/win32/fileio/reparse-points):

> A file or directory can contain a *reparse point*, which is a collection of user-defined data. […] When an
> application sets a reparse point, it stores this data, plus a *reparse tag*, which uniquely identifies the data
> it is storing.

> For example, reparse points are used to implement NTFS file system links […] Reparse points are also used to
> implement mounted folders.

There is "a limit of 63 reparse points on any given path".

[Hard Links and Junctions](https://learn.microsoft.com/en-us/windows/win32/fileio/hard-links-and-junctions):

> A *junction* (also called a *soft link*) differs from a hard link in that the storage objects it references are
> separate directories. A junction can also link directories located on different local volumes on the same
> computer. […] Junctions are implemented through reparse points.

> Hard links can't reference directories, only files, and they can't reference files on different volumes.

That last sentence matters: NTFS cannot express a hard-linked directory, so the only way to build a directory
cycle on NTFS is a reparse point.

Crucially, **junctions and volume mount points share one tag**
([Determining Whether a Directory Is a Mounted Folder](https://learn.microsoft.com/en-us/windows/win32/fileio/determining-whether-a-directory-is-a-volume-mount-point)):

> To determine if the reparse point is a mounted folder (and not some other form of reparse point), test whether
> the tag value equals the value **IO_REPARSE_TAG_MOUNT_POINT**. […] In a similar manner, you can determine if a
> reparse point is a symbolic link by testing whether the tag value is **IO_REPARSE_TAG_SYMLINK**.

### How the JDK classifies them

[`WindowsFileAttributes`](https://github.com/openjdk/jdk/blob/master/src/java.base/windows/classes/sun/nio/fs/WindowsFileAttributes.java):

```java
boolean isDirectoryJunction() {
    return reparseTag == IO_REPARSE_TAG_MOUNT_POINT;
}

public boolean isSymbolicLink() {
    return reparseTag == IO_REPARSE_TAG_SYMLINK;
}

public boolean isDirectory() {
    return ((fileAttrs & FILE_ATTRIBUTE_DIRECTORY) != 0 &&
            (fileAttrs & FILE_ATTRIBUTE_REPARSE_POINT) == 0);
}

public boolean isOther() {
    if (isSymbolicLink())
        return false;
    // return true if device or reparse point
    return ((fileAttrs & (FILE_ATTRIBUTE_DEVICE | FILE_ATTRIBUTE_REPARSE_POINT)) != 0);
}
```

Read carefully:

- `isSymbolicLink()` is **true only for `IO_REPARSE_TAG_SYMLINK`**. A junction is *not* a symbolic link to Java.
  A volume mount point is not either — it is indistinguishable from a junction at this level.
- `isDirectory()` is **false for any reparse point** when attributes are read `NOFOLLOW`, even a directory one.
- Junctions and mount points therefore land in `isOther()`.

`MEASURED`, for a junction, a directory symlink and a file symlink:

| entry | `NOFOLLOW` | `FOLLOW` | `Files.isSymbolicLink` | `readSymbolicLink` |
|---|---|---|---|---|
| junction (`mklink /J`) | `dir=false other=true` | `dir=true` | `false` | `NotLinkException: Reparse point is not a symbolic link` |
| dir symlink (`mklink /D`) | `dir=false symlink=true` | `dir=true` | `true` | target |
| file symlink (`mklink`) | `dir=false symlink=true` | `regular=true` | `true` | target |

### What gets followed — the sharp edge

Javadoc for `Files.walk`
([JDK source, `java/nio/file/Files.java`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/nio/file/Files.java)):

> By default, symbolic links are not automatically followed by this method. If the `options` parameter contains
> the `FOLLOW_LINKS` option then symbolic links are followed.

The implementation reuses the attributes that came free with the directory listing
([`FileTreeWalker::getAttributes`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/nio/file/FileTreeWalker.java)):

```java
if (canUseCached && (file instanceof BasicFileAttributesHolder bfah)) {
    BasicFileAttributes cached = bfah.get();
    if (cached != null && (!followLinks || !cached.isSymbolicLink())) {
        return cached;
    }
}
```

The cache is only bypassed for something the JDK considers a *symbolic link*. A junction is not one, and its
cached `WIN32_FIND_DATA` attributes say `isDirectory() == false`. So:

> **`FOLLOW_LINKS` does not make `Files.walk` descend into a junction or a volume mount point that it meets as a
> directory entry.** Only real symlinks are followed.

`MEASURED`, with `holder/j` a junction to `target/` and `holder/d` a directory symlink to the same `target/`:

```
walk(holder) default          -> holder, holder\d, holder\j, holder\plain.txt
walk(holder) FOLLOW_LINKS     -> holder, holder\d, holder\d\inner, holder\d\inner\deep.txt,
                                 holder\d\t.txt, holder\j, holder\plain.txt
walk(holder\j) default        -> holder\j                         (start path, not descended)
walk(holder\j) FOLLOW_LINKS   -> holder\j, holder\j\inner, holder\j\inner\deep.txt, holder\j\t.txt
```

The junction *is* descended when it is the **start** of the walk with `FOLLOW_LINKS`, because
`FileTreeWalker.walk(start)` calls `visit(start, /* canUseCached */ false)` and the fresh read follows the
reparse point. Same path, different treatment depending on whether it is the root or an entry.

`walkFileTree` sees the junction as a plain file:

```
preVisitDirectory holder
preVisitDirectory d
preVisitDirectory inner
visitFile deep.txt  dir=false link=false other=false
visitFile t.txt     dir=false link=false other=false
visitFile j         dir=false link=false other=true    <-- the junction
visitFile plain.txt dir=false link=false other=false
```

### Cycle detection

`Files.walk` Javadoc:

> If the `options` parameter contains the `FOLLOW_LINKS` option then the stream keeps track of directories
> visited so that cycles can be detected. A cycle arises when there is an entry in a directory that is an
> ancestor of the directory. Cycle detection is done by recording the `file-key` of directories, or if file keys
> are not available, by invoking the `isSameFile` method to test if a directory is the same file as an ancestor.
> When a cycle is detected it is treated as an I/O error with an instance of `FileSystemLoopException`.

`walkFileTree` says the same, adding that `visitFileFailed` is the callback that receives it.
`FileSystemLoopException` extends `FileSystemException` extends `IOException`
([Javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileSystemLoopException.html)).

Two implementation facts:

1. **Cycle detection only runs with `FOLLOW_LINKS`** — `FileTreeWalker.visit` guards it with
   `if (followLinks && wouldLoop(...))`.
2. **`fileKey()` is `null` on Windows**:

   ```java
   @Override
   public Object fileKey() {
       return null;
   }
   ```

   so `wouldLoop` always takes the slow branch — a `Files.isSameFile` call against *every* ancestor on the
   stack, each of which opens handles. And it swallows failures:

   ```java
   } catch (IOException e) {
       // ignore
   }
   ```

`MEASURED`, with both a junction and a directory symlink pointing at their own ancestor:

- default walk: neither is descended, no loop, no error.
- `walkFileTree` + `FOLLOW_LINKS`: `visitFileFailed(dirlink) -> java.nio.file.FileSystemLoopException`. **The
  junction produced no event at all** — consistent with "junctions are never followed".
- `Files.walk` + `FOLLOW_LINKS`: the same loop surfaces as `UncheckedIOException` wrapping
  `FileSystemLoopException` (see §4).

Net: on NTFS, an unguarded `Files.walk` cannot spin forever — junctions are not followed, symlink cycles are
detected, and NTFS cannot hard-link a directory. That safety comes from a quirk of attribute caching, not from a
specified guarantee.

### Windows vs Linux

- Junctions, volume mount points and the `IO_REPARSE_TAG_MOUNT_POINT` tag have **no Linux analogue**. Everything
  above about them is untestable on Linux.
- `UnixFileAttributes.fileKey()` returns a real key
  ([source](https://github.com/openjdk/jdk/blob/master/src/java.base/unix/classes/sun/nio/fs/UnixFileAttributes.java)):
  ```java
  public UnixFileKey fileKey() {
      ...
      key = new UnixFileKey(st_dev, st_ino);
      ...
  }
  ```
  so on Linux cycle detection is a cheap hash comparison, and on Windows it is O(depth) file opens per directory.
  A performance test of deep trees will not reproduce across platforms.
- Symlink behaviour (default not followed; `FOLLOW_LINKS` followed; cycles reported as
  `FileSystemLoopException`) **is** the same on both, so symlink-cycle tests can run on Linux CI. On Windows,
  creating a symlink needs elevation or Developer Mode; a junction does not.
- Bind mounts on Linux *are* followed by `Files.walk` (they are ordinary directories) — the opposite of a Windows
  mount point. `UNSOURCED`: not verified, no Linux machine in this environment.

---

## 4. How failures surface

### The specified taxonomy

`Files.walkFileTree` Javadoc:

> For each file encountered this method attempts to read its `BasicFileAttributes`. If the file is not a
> directory then the `visitFile` method is invoked with the file attributes. If the file attributes cannot be
> read, due to an I/O exception, then the `visitFileFailed` method is invoked with the I/O exception.

> Where the file is a directory, and the directory could not be opened, then the `visitFileFailed` method is
> invoked with the I/O exception, after which, the file tree walk continues, by default, at the next *sibling*
> of the directory.

> Where the directory is opened successfully, then the entries in the directory, and their *descendants* are
> visited. When all entries have been visited, or an I/O error occurs during iteration of the directory, then the
> directory is closed and the visitor's `postVisitDirectory` method is invoked.

> Where a visit method terminates due an `IOException`, an uncaught error, or runtime exception, then the
> traversal is terminated and the error or exception is propagated to the caller of this method.

> If a visitor returns a result of `null` then `NullPointerException` is thrown.

[`FileVisitor`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/FileVisitor.html)
scopes `visitFileFailed` widely — "This method is invoked if the file's attributes could not be read, the file is
a directory that could not be opened, and other reasons" — and `postVisitDirectory` receives "`null` if the
iteration of the directory completes without an error; otherwise the I/O exception that caused the iteration of
the directory to complete prematurely".

### The `Files.walk` sharp edge

`Files.walk` Javadoc:

> If an `IOException` is thrown when accessing the directory after this method has returned, it is wrapped in an
> `UncheckedIOException` which will be thrown from the method that caused the access to take place.

and it declares `@throws IOException if an I/O error is thrown when accessing **the starting file**`. That split
is exactly what
[`FileTreeIterator`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/nio/file/FileTreeIterator.java)
implements — the start file's error is checked, everything later is not:

```java
this.next = walker.walk(start);
...
IOException ioe = next.ioeException();
if (ioe != null)
    throw ioe;
```

```java
private void fetchNextIfNeeded() {
    if (next == null) {
        FileTreeWalker.Event ev = walker.next();
        while (ev != null) {
            IOException ioe = ev.ioeException();
            if (ioe != null)
                throw new UncheckedIOException(ioe);
            ...
```

`FileTreeWalker.visit` shows what becomes such an event: an unreadable attribute set, a directory that
`Files.newDirectoryStream` refuses to open, and a detected loop:

```java
try {
    attrs = getAttributes(entry, canUseCached);
} catch (IOException ioe) {
    return new Event(EventType.ENTRY, entry, ioe);
}
...
if (followLinks && wouldLoop(entry, attrs.fileKey())) {
    return new Event(EventType.ENTRY, entry,
                     new FileSystemLoopException(entry.toString()));
}
...
try {
    stream = Files.newDirectoryStream(entry);
} catch (IOException ioe) {
    return new Event(EventType.ENTRY, entry, ioe);
}
```

So for `Files.walk` / `Files.find` there is **no per-entry error hook at all**. One unreadable subdirectory
anywhere in the tree aborts the whole stream with an unchecked exception, from whatever terminal operation
happened to pull the element. `walkFileTree` is the only API that can skip a bad entry and carry on.

### `MEASURED`

Directory `acl/` containing `keep.txt`, `nofile.txt` (read denied) and `denied/` (RX denied via `icacls`):

| call | outcome |
|---|---|
| `Files.exists(denied)` | `true` |
| `Files.isDirectory(denied)` | `true` |
| `Files.isReadable(denied)` | `false` |
| `Files.readAttributes(denied, …)` | **succeeds** — `dir=true` |
| `Files.newDirectoryStream(denied)` | `AccessDeniedException` (checked, eager) |
| `Files.list(denied)` | `AccessDeniedException` (checked, eager) |
| `Files.walk(denied)` | `AccessDeniedException` (checked — it is the start file) |
| `Files.readAttributes(nofile.txt, …)` | **succeeds**, `size=3` |
| `Files.readString(nofile.txt)` | `AccessDeniedException` |
| `Files.walk(acl).forEach(...)` | delivers `acl` then throws `UncheckedIOException` ⇐ `AccessDeniedException`; nothing after it |
| `Files.walk(acl).collect(...)` | same abort — no partial list |
| `Files.find(acl, …).count()` | same abort |
| `Files.walkFileTree(acl, …)` | `preVisitDirectory acl`, `visitFileFailed denied -> AccessDeniedException`, `visitFile keep.txt`, `visitFile nofile.txt`, `postVisitDirectory acl exc=null` — **completes** |
| file deleted between listing and `readAttributes` | `NoSuchFileException` |

Note `readAttributes` succeeding on an ACL-denied *directory* and on an unreadable *file*: directory-entry
metadata comes from the parent's listing, so a census that only reads attributes sees more than a census that
opens files. And permissions on the *file* do not hide it from a walk.

### Silently skipped

- `.` and `..` are never emitted.
  [`DirectoryStream`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/java/nio/file/DirectoryStream.java):
  "Some file systems maintain special links to the directory itself and the directory's parent directory. Entries
  representing these links are not returned by the iterator." Enforced in
  [`WindowsDirectoryStream`](https://github.com/openjdk/jdk/blob/master/src/java.base/windows/classes/sun/nio/fs/WindowsDirectoryStream.java)
  by `isSelfOrParent`.
- `Files.exists` / `isDirectory` / `isReadable` never throw: an over-long path, a permission problem or a race
  all collapse to `false`.
- `wouldLoop` swallows `IOException` (quoted above), so a cycle check that itself fails is silently treated as
  "no cycle".
- `Files.copy` with `COPY_ATTRIBUTES` swallows the attribute-copy failure for files:
  ```java
  try {
      copySecurityAttributes(source, target, followLinks);
  } catch (IOException x) {
      // ignore
  }
  ```
  ([`WindowsFileCopy`](https://github.com/openjdk/jdk/blob/master/src/java.base/windows/classes/sun/nio/fs/WindowsFileCopy.java))

### `DirectoryStream` iteration errors

`DirectoryStream` Javadoc:

> If an I/O error is encountered when accessing the directory then it causes the `Iterator`'s `hasNext` or `next`
> methods to throw `DirectoryIteratorException` with the `IOException` as the cause.

Confirmed in `WindowsDirectoryStream`, which also wraps an `IOException` thrown by a user filter:

```java
} catch (WindowsException x) {
    IOException ioe = x.asIOException(dir);
    throw new DirectoryIteratorException(ioe);
}
```

So a raw `newDirectoryStream` loop must catch **two** shapes: `IOException` on open, `DirectoryIteratorException`
(unchecked) during iteration.

### `SecurityException`

The `java.nio.file` Javadoc still documents `@throws SecurityException` on most methods, but that path is dead on
a modern JDK. [JEP 486: Permanently Disable the Security Manager](https://openjdk.org/jeps/486) — *Closed /
Delivered*, **release 24**:

> Remove the ability to enable the Security Manager when starting the Java runtime
> (`java -Djava.security.manager ...`). Remove the ability to install a Security Manager while an application is
> running (`System.setSecurityManager(...)`). […] Revise the specification of the Security Manager API so that
> all implementations of it behave as if no Security Manager is ever enabled.

`MEASURED`: `System.getSecurityManager() == null` and the call is deprecated for removal. On JDK 26, which this
project targets, **no file-walking failure can surface as `SecurityException`**. Access-control problems arrive
as `AccessDeniedException` (an `IOException`), which is what we saw.

### Windows vs Linux

The exception *types* are provider-independent — `AccessDeniedException`, `NoSuchFileException`,
`NotDirectoryException`, `FileSystemLoopException`, `DirectoryIteratorException`, `UncheckedIOException`. The
`Files.walk`-aborts / `walkFileTree`-continues asymmetry is in shared code and is **fully testable on Linux CI**.

What differs:

- *Which* operation fails. On Linux, a directory missing `r` fails at `readdir` while one missing `x` fails when
  stat-ing children; NTFS ACLs are one `AccessDeniedException` at open time. The Windows observation that
  `readAttributes` still succeeds on an unreadable directory is a Windows fact (`UNSOURCED` for Linux — not
  verified here).
- Making a file unreadable: `chmod` on Linux vs `icacls` on Windows; and on Windows an administrator or the owner
  can often read regardless, so a "denied" fixture is less reliable.
- Cycle-detection cost and reliability differ because of `fileKey()` (§3).

---

## 5. Unicode

### NTFS does not normalise

[Maximum Path Length Limitation](https://learn.microsoft.com/en-us/windows/win32/fileio/maximum-file-path-limitation):

> There is no need to perform any Unicode normalization on path and file name strings for use by the Windows file
> I/O API functions because the file system treats path and file names as an opaque sequence of **WCHAR**s. Any
> normalization that your application requires should be performed with this in mind, external of any calls to
> related Windows file I/O API functions.

[Naming Files, Paths, and Namespaces](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file):

> On newer file systems, such as NTFS, exFAT, UDFS, and FAT32, Windows stores the long file names on disk in
> Unicode, which means that the original long file name is always preserved.

Forbidden characters are `< > : " / \ | ? *`, NUL, and 1–31; names must not end in a space or period; the
`CON`/`PRN`/`AUX`/`NUL`/`COM#`/`LPT#` device names are reserved in every directory.

### The Java ↔ NTFS conversion is lossless

On Windows the JDK hands `char[]` straight to the wide Win32 API, with no charset in the middle
([`WindowsNativeDispatcher`](https://github.com/openjdk/jdk/blob/master/src/java.base/windows/classes/sun/nio/fs/WindowsNativeDispatcher.java)):

```java
static NativeBuffer asNativeBuffer(String s) throws WindowsException {
    ...
    int stringLengthInBytes = s.length() << 1;
    ...
    char[] chars = s.toCharArray();
    unsafe.copyMemory(chars, Unsafe.ARRAY_CHAR_BASE_OFFSET, null,
        buffer.address(), (long)stringLengthInBytes);
    unsafe.putChar(buffer.address() + stringLengthInBytes, (char)0);
```

A Java `String` *is* a UTF-16 code-unit sequence, and so is an NTFS name, so the round trip is bit-exact —
including sequences that are not valid Unicode.

`MEASURED`:

- `café.txt` (NFC, `U+00E9`) and `cafe\u0301.txt` (NFD, `U+0065 U+0301`) coexist as **two distinct files** with
  different contents, and `Files.list` returns each with its own code points. NTFS performs no normalisation, and
  neither does the JDK.
- A supplementary-plane name (`emoji-😀.txt`) round-trips; `getFileName().toString().length() == 12` (surrogate
  pair counted as two `char`s).
- A name containing an **unpaired high surrogate** (`lone-\uD83D.txt`) was created and listed back as exactly
  `U+006C U+006F U+006E U+0065 U+002D U+D83D U+002E U+0074 U+0078 U+0074`. Lone surrogates survive intact.

**Answer to "can two distinct NTFS names collide as Java Strings?" — No.** `String.equals` on Windows
distinguishes every pair of distinct NTFS names. But two distinct NTFS names *can* collide

- as `Path` values, through case folding (§2 — including the three pairs where the JDK folds and NTFS does not),
  and
- as Strings the moment *you* normalise them yourself (`Normalizer.normalize`, `toLowerCase`, NFC-on-ingest), and
  NFC/NFD siblings do occur in the wild (files created on macOS, unpacked archives).

### Windows vs Linux

Linux filenames are byte strings, and the JDK converts with `sun.jnu.encoding`:

- **String → bytes** throws rather than mangling
  ([`UnixPath`](https://github.com/openjdk/jdk/blob/master/src/java.base/unix/classes/sun/nio/fs/UnixPath.java)):
  ```java
  private static byte[] encode(UnixFileSystem fs, String input) {
      try {
          return JLA.uncheckedGetBytesOrThrow(input, Util.jnuEncoding());
      } catch (CharacterCodingException cce) {
          throw new InvalidPathException(input,
              "Malformed input or input contains unmappable characters");
      }
  }
  ```
- **bytes → String is lossy**. `UnixPath::toString` calls
  [`Util.toString`](https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/sun/nio/fs/Util.java):
  ```java
  static String toString(byte[] bytes) {
      return new String(bytes, jnuEncoding);
  }
  ```
  and `new String(byte[], Charset)` is specified to "always replace malformed-input and unmappable-character
  sequences with this charset's default replacement string" (U+FFFD).

So **on Linux two distinct filenames can decode to the same Java String** whenever their bytes are invalid under
`sun.jnu.encoding` (typically UTF-8) — the exact collision that cannot happen on Windows. A "distinct names never
collide" test would pass on Windows and could fail on Linux; a "lone surrogate in a filename" test is
Windows-only (such a name is unrepresentable as Linux bytes and `encode` would throw `InvalidPathException`).

macOS is out of scope: `UNSOURCED`, I did not fetch a source for APFS/HFS+ normalisation.

---

## 6. Metadata: `mtime`

### Precision

[File Times](https://learn.microsoft.com/en-us/windows/win32/sysinfo/file-times):

> A *file time* is a 64-bit value that represents the number of 100-nanosecond intervals that have elapsed since
> 12:00 A.M. January 1, 1601 Coordinated Universal Time (UTC).

> The NTFS file system stores time values in UTC format, so they are not affected by changes in time zone or
> daylight saving time.

> Not all file systems can record creation and last access times, and not all file systems record them in the
> same manner. For example, the resolution of create time on FAT is 10 milliseconds, while write time has a
> resolution of 2 seconds and access time has a resolution of 1 day […] The NTFS file system delays updates to
> the last access time for a file by up to 1 hour after the last access.

> The only guarantee about a file time stamp is that the file time is correctly reflected when the handle that
> makes the change is closed.

The JDK converts without loss
([`WindowsFileAttributes`](https://github.com/openjdk/jdk/blob/master/src/java.base/windows/classes/sun/nio/fs/WindowsFileAttributes.java)):

```java
static FileTime toFileTime(long time) {
    try {
        long adjusted = Math.addExact(time, WINDOWS_EPOCH_IN_100NS);
        long nanos = Math.multiplyExact(adjusted, 100L);
        return FileTime.from(nanos, TimeUnit.NANOSECONDS);
    } catch (ArithmeticException e) {
        ...
```

`MEASURED`:

- A freshly written file reported `2026-08-22T20:37:58.0550041Z`; `nanos % 100 == 0` always.
- Setting `2020-01-01T00:00:00.123456789Z` read back as `2020-01-01T00:00:00.123456700Z`.

**NTFS `mtime` is exactly 100 ns (7 fractional digits).** Anything finer is truncated, matching the `Files.copy`
Javadoc caveat "Copying of file timestamps may result in precision loss". Do not rely on `atime` at all — NTFS
may be up to an hour stale.

### Stability across copy and move — `MEASURED`

Source `mtime` pinned to `2020-01-01T00:00:00.1234567Z`, on NTFS:

| operation | `mtime` | `creationTime` |
|---|---|---|
| `Files.copy` (no options) | **preserved** | new (now) |
| `Files.copy(COPY_ATTRIBUTES)` | preserved | new (now) |
| `Files.move`, same volume (C:→C:) | preserved | *(not sampled)* |
| `Files.move`, cross volume (C:→D:) | preserved | new (now) |
| `Files.copy`, cross volume (C:→D:) | preserved | new (now) |

`mtime` survived every path tested, to the full 100 ns. `creationTime` did **not** survive any copy, and did not
survive a cross-volume move — expected, since `WindowsFileCopy` uses `MoveFileEx` with `MOVEFILE_COPY_ALLOWED`
for a cross-volume move, i.e. a create-and-delete.

Be careful with the first row. The preservation for plain `Files.copy` comes from `CopyFileEx`, and the
[`CopyFileExW` reference](https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-copyfileexw)
does **not** promise timestamps:

> This function preserves extended attributes, OLE structured storage, NTFS file system alternate data streams,
> security resource attributes, and file attributes.

"File attributes" here means the DOS attribute bits. **`UNSOURCED`: I found no Microsoft statement that
`CopyFileEx` copies the last-write time.** It does in practice on Windows 11, but that is an observation, not a
contract — and it is not portable (see below). What *is* specified is Java's own `COPY_ATTRIBUTES`
(`java/nio/file/Files.java`):

> Attempts to copy the file attributes associated with this file to the target file. The exact file attributes
> that are copied is platform and file system dependent and therefore unspecified. Minimally, the
> `last-modified-time` is copied to the target file if supported by both the source and target file stores.
> Copying of file timestamps may result in precision loss.

So: if `mtime` must survive a copy, pass `COPY_ATTRIBUTES` — that is the only contractual guarantee.

### Windows vs Linux

- **Plain `Files.copy` preserves `mtime` on Windows but not on Linux.** On Unix the JDK copies timestamps only
  when `COPY_ATTRIBUTES` was requested
  ([`UnixFileSystem`](https://github.com/openjdk/jdk/blob/master/src/java.base/unix/classes/sun/nio/fs/UnixFileSystem.java)):
  ```java
  if (flags.copyBasicAttributes) {
      ...
      attrs.lastModifiedTime().to(TimeUnit.NANOSECONDS));
  ```
  A test asserting "mtime survives `Files.copy`" passes on Windows and **fails on Linux**.
- The 100 ns truncation is an NTFS property. ext4 stores nanoseconds; the truncation assertion is NTFS-only.
- `creationTime()` on Linux is `UNSOURCED` here — I did not verify what the JDK returns when the kernel/filesystem
  exposes no birth time. Do not build cross-platform behaviour on `creationTime`.
- `atime` semantics differ (NTFS's 1-hour lag vs Linux mount options such as `relatime`/`noatime`). Unusable on
  both, for different reasons.

---

## Windows-only, at a glance

For [#18](https://github.com/algernon28/vespera/issues/18) — what a Linux CI runner **cannot** verify:

| Behaviour | Why not on Linux |
|---|---|
| `>MAX_PATH` paths, the `\\?\` prefix, the 32,000-char ceiling, `Files.exists` silently false | Windows-only concepts |
| `Path.equals` / `hashCode` case-insensitivity; `toRealPath` casing canonicalisation | `UnixPath` is byte-exact |
| The three folding-table divergences and the `Files.isSameFile` short-circuit | ditto |
| Per-directory case sensitivity (`fsutil`) | NTFS-only (also needs elevation on Windows) |
| Junctions and volume mount points; "`FOLLOW_LINKS` does not follow a junction entry"; `isOther()` for reparse points | no analogue |
| `fileKey() == null` and the `isSameFile`-based cycle detection cost | Linux has real file keys |
| NTFS 100 ns `mtime` truncation | filesystem-specific |
| Plain `Files.copy` preserving `mtime` | Windows-only side effect of `CopyFileEx` |
| Filenames containing unpaired surrogates | unrepresentable on Linux |

Testable on Linux and therefore safe to put in normal CI: default-vs-`FOLLOW_LINKS` symlink traversal,
`FileSystemLoopException` on a symlink cycle, `Files.walk` aborting with `UncheckedIOException` versus
`walkFileTree` continuing via `visitFileFailed`, `DirectoryIteratorException`, `.`/`..` never being emitted, and
`NoSuchFileException` on a delete-during-walk race.

---

## Consequences for occurrence identity ([#4](https://github.com/algernon28/vespera/issues/4))

Constraints this research puts on "how a file occurrence's path identifies it" — stated as facts, not as a
recommendation:

1. **`Path` is not a safe identity key on Windows.** `Path.equals`/`hashCode` fold case with
   `Character.toUpperCase`, which does not match NTFS. Two genuinely distinct files can be one `HashMap` key
   (measured for `ı`/`I`, `µ`/`μ`, `ſ`/`s`). `Files.isSameFile` is not a fix — it short-circuits on the same
   comparison and returns `true` for those pairs.
2. **`String` identity is exact on Windows but case-blind to nothing.** A raw `Path::toString` distinguishes
   every distinct NTFS name, but the *same* file reached via a differently-cased path yields a different string.
   Any case normalisation you apply reintroduces problem 1; any Unicode normalisation (NFC) collapses NFC/NFD
   siblings that NTFS keeps distinct. `toRealPath()` is the only primitive that returns the on-disk casing.
3. **There is no inode-equivalent available.** `BasicFileAttributes.fileKey()` returns `null` on Windows, so an
   identity scheme cannot fall back to a stable file id through public API — unlike Linux, where
   `fileKey()` is `(st_dev, st_ino)`. Anything identity-by-content-location must be path-based on Windows.
4. **Path strings can be long.** Up to ~32,000 characters is reachable through `java.nio.file`; a
   `path`/`occurrence` column and any index on it must accommodate that, and the JDK raises a plain `IOException`
   above the ceiling.
5. **`mtime` is a 100 ns value that survives copies and moves on NTFS but not portably.** Usable as a
   change-detection token within one Windows volume; not usable as an identity component, and not reproducible on
   Linux without `COPY_ATTRIBUTES`.
6. **The same physical file can appear at more than one path**, via junctions, directory symlinks and volume
   mount points — and the default walk emits each such reparse point once, as a leaf with `isOther() == true`,
   without descending. Census will therefore *not* double-count through them by default, and a walk that opts
   into `FOLLOW_LINKS` will double-count through directory symlinks but still not through junctions.
7. **Census cannot use `Files.walk` if it must survive a bad directory.** One `AccessDeniedException` aborts the
   whole stream with `UncheckedIOException` and the traversal cannot be resumed; only
   `walkFileTree`/`visitFileFailed` can record the anomaly and continue. (Relevant to Stage 0 Census, which is
   specified as pure measurement.)

---

## What I could not source

- **The `$UpCase` table.** No Microsoft documentation found for the per-volume NTFS uppercase table, so the
  *reason* the folding tables diverge, and whether the divergence depends on the Windows version that formatted
  the volume, is unsourced. The divergence itself is measured.
- **No OpenJDK bug report** for the `Path.equals` folding mismatch or the `Files.isSameFile` short-circuit.
  Searched JBS; nothing found. Both are read directly off the source and measured.
- **`CopyFileEx` and timestamps.** Not documented; the preservation is measured only.
- **Per-directory case sensitivity under Java.** Inferred from `WindowsPath` source; not measured (needs
  elevation).
- **`LongPathsEnabled` A/B.** Could not toggle the registry key (needs elevation), so the long-path result is not
  isolated from it; attribution rests on the JDK source plus JDK-8348664.
- **Anything Linux-side that needed running code.** All Linux claims here come from OpenJDK source or Javadoc, not
  from execution: bind-mount traversal, `readAttributes` on an unreadable directory, and `creationTime()`
  fallback are explicitly unverified.
- **JDK-8315748**, cited in the ticket as possibly relevant, is *"Cells in VirtualFlow jump after resizing"*
  (JavaFX, fixed in jfx17.0.9) — unrelated to long paths. The relevant issues are JDK-8348664, JDK-8320371,
  JDK-8317555, JDK-8315405 and JDK-8366403, linked above.
