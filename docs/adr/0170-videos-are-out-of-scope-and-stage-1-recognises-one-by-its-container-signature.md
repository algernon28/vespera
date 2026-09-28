# ADR-170 — Videos are out of scope, and stage 1 recognises one by its container signature

- **Date**: 2026-09-28
- **Status**: accepted
- **Extends**: [ADR-146](0146-spreadsheets-are-out-of-scope-and-stage-1-removes-them-with-a-verdict-of-their-own.md), as [ADR-167](0167-bmp-images-are-out-of-scope-and-stage-1-recognises-one-by-its-file-header-and-the-header-after-it.md) did. A third kind of file joins `pipeline`'s `OutOfScope`, under the same `out-of-scope` verdict and in the same place in stage 1's first pass. The vocabulary stays at nine values.
- **Builds on**: [ADR-094](0094-stage-1-decides-what-a-file-is-from-its-bytes-the-extension-may-only-narrow-within-that-class.md) and [ADR-095](0095-the-detected-format-is-a-stage-1-output-and-unrecognised-content-earns-no-verdict-until-the-mix-is-measured.md). A video is recognised from its bytes, as a new `DetectedFormat`, `VIDEO`, and the name plays no part. ADR-095's mix has now been measured for these files: they were the `00 00 00 18` rows among the unrecognised leading bytes.
- **Keeps**: [ADR-100](0100-docling-reads-the-bytes-too-so-stage-2-sends-a-canonical-extension-derived-from-the-detected-format-and-nothing-else.md). `VIDEO` gets the row `UNRECOGNISED` has in `extraction`'s table, the neutral name, which is the name every video was posted under until now. No posted name changes and the naming scheme stays at version 1.
- **Not the lasting answer**: [#326](https://github.com/algernon28/vespera/issues/326). A file Docling fails on, for whatever reason, should become `extraction-failed` and the run should go on, with the files it failed on listed for review at the end. This record decides none of that. It is a quick unblock the operator asked for on 2026-09-28 (*"skip all the video formats, no matter which one"*) so that the whole-archive run can go past stage 2.

## Context

On 2026-09-28 the whole-archive run (43,101 files, corpus root `H:\Working docs corpora\QA - DIGITAL - Recupero KT PERIN`, working directory `H:\Working docs corpora\Vespera_working_dir`, jar built from main at 4050b25, which carries ADR-167) failed in stage 2 at 12:03:22 local time, after 37 minutes 45 seconds, with 4,512 read, 2,492 written and 2,004 filtered. The exception was `404 Not Found: {"detail":"Task result not found. Please wait for a completion status."}`, thrown from `DoclingClient.convert` as RestClient's `HttpClientErrorException$NotFound`. That is not a `ServiceScopeFailureException`, so the skip policy did not absorb it and stage 2 failed.

### What Docling did with the file

docling-serve's own log, at the same second:

- Two uploads arrived with `content_type=video/mp4`, of 13,110,954 and 12,624,254 bytes.
- Docling detected each as `InputFormat.VIDEO` and started its `VideoPipeline`, whose first step is the ASR transcriber.
- Each job failed with *"whisper is not installed"*.
- The next answer on `POST /v1/convert/file` was HTTP 404.

So **a failed Docling job surfaces on the synchronous endpoint as a 404**, not as a response whose status is `failure`. That is why ADR-139's path, from a `failure` status through a fault row to `extraction-failed`, never saw it: no status came back, only an exception.

**It is not a timeout.** docling-serve's `max_sync_wait` is 120 s, and no conversion in the run took longer than 91.3 s.

### Why a video reached Docling at all

Stage 1 knew no video signature, so an MP4 was `UNRECOGNISED`. `DoclingClient.extensionFor` posts `UNRECOGNISED` under the neutral name, so that Docling's own sniffing decides (ADR-100). Docling's sniffing recognised the bytes as video, and sent them down the one pipeline this sidecar image cannot run.

### What the archive holds

A survey of the archive's leading bytes, for this record:

- **8 videos, every one ISO base media** (`ftyp` at offset 4): 1 with major brand `isom` and 7 with `mp42`, between 5 and 33 MB. Among them `A&RD_SmartPOS_BaseDocumentale\...\Varie\video\Stacco_bianco_e_pressione_tasto_rosso.mp4` and `...\MobilePOS\TEST-18-agosto-versione_offuscata\Test#1\PAX_1.0.36_offuscata_iniz_carta_bicocca.mp4`. The format-mix page listed `00 00 00 18` seven times among the unrecognised leading bytes: a 24-byte `ftyp` box, the `mp42` files.
- **No** Matroska or WebM, AVI, ASF, FLV, MPEG program stream or Ogg file.
- **23 `RIFF` files that are not WEBP.** They are CorelDRAW drawings (`RIFF....CDRC`), not video, and they must stay `UNRECOGNISED`.

So on this archive the rule could be `ftyp` alone. The operator asked for every video whatever its container, and a signature list is cheap, so the Decision covers the containers a video is commonly kept in.

## Decision

**A video is out of scope, whatever it holds and whatever holds it.** Stage 1 removes it with the `out-of-scope` verdict of ADR-146, in the same first pass, before anything hashes or converts it. Its reason reads *"a video, and videos are out of scope"*.

**Stage 1 recognises a video as a new `DetectedFormat`, `VIDEO`.** A format of its own and not a subtype, for the reasons ADR-167 gave for `BMP`: the bytes decide it, and a subtype is what a name adds inside a class the bytes have already fixed.

### The signatures

All of them are read from the 512 bytes stage 1 already reads, so this costs no second read. A file is `VIDEO` when any one of these holds.

1. **ISO base media and QuickTime** (MP4, MOV, M4V, 3GP, 3G2, F4V and the rest): the four bytes at offset 4 are `ftyp`, and
   - the box size, the big-endian number at offset 0, is at least 16 (size, type, major brand and minor version) and at most 512, so the whole box lies inside the prefix. That also makes the first two bytes zero, so no text file can match;
   - the major brand, at offset 8, is not an **audio-only brand**: `M4A `, `M4B `, `M4P `, `F4A `, `F4B `. Only the major brand counts here, because video files list audio brands among their compatible ones: an iTunes `M4V ` file lists `M4A `;
   - neither the major brand nor any compatible brand (from offset 16 to the end of the box, as far as the prefix holds) is a **still-image brand**:
     - ISO/IEC 23008-12 (HEIF), as registered with the MP4 Registration Authority: `mif1`, `mif2`, `msf1`, `heic`, `heix`, `heim`, `heis`, `hevc`, `hevx`, `hevm`, `hevs`, `avci`, `avcs`, `jpeg`, `jpgs`, `vvic`, `vvis`, `1pic`;
     - AVIF: `avif`, `avio`, `avis`;
     - MIAF (ISO/IEC 23000-22): `miaf`;
     - Canon's raw format CR3, which is ISO base media too: `crx `.

     Every HEIF file must carry one of its structural brands, `mif1`, `mif2` or `msf1`, among its compatible brands, and every AVIF file carries `mif1` and `miaf`. So reading the compatible brands catches a HEIF or AVIF file whatever its major brand, and the list above is belt and braces. The image-sequence brands (`msf1`, `hevc`, `avis` and the like) are on the list because a HEIF image sequence is an animated picture, not a recording, and because the operator asked about video.
2. **QuickTime without `ftyp`**, as older QuickTime writers produced: the four bytes at offset 4 are `moov`, `mdat`, `wide` or `pnot`, and the prefix does not decode as text. The text condition matters: *"The wide range…"* has `wide` at offset 4. `free` and `skip` are **not** on the list. They are padding any ISO base media writer may lead with, a HEIF file among them, and none of the eight videos starts with one. A QuickTime file that does stays `UNRECOGNISED`.
3. **Matroska and WebM**: the EBML magic `1A 45 DF A3` at offset 0. Matroska and WebM are the only EBML document types in use, so the magic is not narrowed by the DocType it carries.
4. **AVI**: `RIFF` at offset 0 and the form type `AVI ` at offset 8. No other `RIFF` form is video. CorelDRAW's `CDRC`, WAVE's `WAVE` and every other form stay what they are today, and WEBP stays `IMAGE`.
5. **ASF** (WMV): the 16-byte header object GUID `30 26 B2 75 8E 66 CF 11 A6 D9 00 AA 00 62 CE 6C` at offset 0.
6. **FLV**: `FLV` and the version byte `01` at offset 0.
7. **MPEG program stream** (`.mpg`, `.vob`): `00 00 01 BA` at offset 0. **MPEG video elementary stream** (`.m1v`, `.m2v`): `00 00 01 B3` at offset 0.
8. **MPEG transport stream** (`.ts`): the sync byte `47` at offsets 0, 188 and 376, and the prefix does not decode as text. **BDAV transport stream** (`.m2ts`, `.mts`), whose packets carry a 4-byte timestamp ahead of each: `47` at offsets 4, 196 and 388, under the same text condition. `47` is `G`, and a text file may open with it, so three syncs one packet apart and a binary prefix are both required. A file too short to carry the third sync is not a transport stream here.
9. **Ogg carrying Theora**: `OggS` at offset 0 and the Theora identification header, `80` followed by `theora`, anywhere in the prefix. An Ogg file holding only Vorbis, Opus or FLAC stays `UNRECOGNISED`.
10. **RealMedia**: `.RMF` and then two zero bytes (the top of the header's size field) at offset 0. The zero bytes keep a text file opening with *".RMF"* out.
11. **MXF**: the SMPTE ST 377-1 header partition pack key's fourteen fixed bytes `06 0E 2B 34 02 05 01 01 0D 01 02 01 01 02` at offset 0.

### Where the test sits in stage 1

- **After `BMP` and before `IMAGE`**, for the signatures that need no text condition: 1 and 3 to 7, 9, 10 and 11. None of them can match a file an image signature matches, and a video must be caught before any later branch reads it as something else.
- **Last, just before `UNRECOGNISED`**, for the two whose condition is that the prefix does not decode as text: 2 and 8. Placing them after the text branch is what the condition means. It also means a GIF, which opens with `G`, is decided as an image before a transport stream is ever considered.

### What is not decided here

- **Audio is not out of scope.** Docling would fail on an audio file the same way, because it too goes through the ASR pipeline. The operator asked about video, and a WAV, an M4A or an Ogg Vorbis file stays `UNRECOGNISED` and is sent as before. That is a later decision, or #326's.
- **Some audio is read as video all the same**, where the container cannot say which it holds without parsing its tracks: an ASF file holding only audio (a WMA), a Matroska file holding only audio (`.mka`, `.weba`), an FLV or RealMedia file with no video stream, and an ISO base media file holding only audio under a general brand such as `isom` or `mp42`. Each is removed with a reason that calls it a video. That is accepted: telling them apart would mean the track parsing ADR-068 keeps out of stage 1, and each would fail in Docling in the same way.

### The rest follows ADR-167

- **It is sent to Docling exactly as before.** `VIDEO` maps to the neutral name, as `UNRECOGNISED` does. The naming scheme stays at version 1 and the extractor identity does not change. That matters for a seed, which stage 1 never judges, and for a later decision to put videos back.
- **The seed side is unchanged.** A video in the seed folder is converted as before, and on this sidecar image it will fail as the corpus's did. That is #326's to fix.
- **ADR-150 §4's rule that an image survivor shows no pictures covers `VIDEO` too.** A still Docling took from a video would be a re-sampled frame of the original, not a second document worth carrying alongside it, which is the reason §4 gives for an image. No `VIDEO` survivor can exist while this record stands, so no test reaches that branch.
- **The format-mix page names them.** Videos get a row of their own, *"Videos"*, after *"BMP images"*, and its sentence about what is out of scope reads *"Spreadsheets, BMP images and videos are out of scope, whatever they hold."* They are counted in *"Files left out as out of scope, and not read any further"*.

## Alternatives rejected

- **Only the brands known to be video**, instead of every `ftyp` file but the still-image and audio-only brands. It would miss a video under a brand nobody listed, and that file would stop the run exactly as the MP4s did. The operator asked for every video. The kinds of ISO base media that are not video are few and registered, so the exclusions are the shorter and safer list.
- **Recognising a video by its extension.** ADR-094 does not let a name decide a format.
- **Treating the 404 as a failed conversion.** That is the lasting fix, and it is #326's: it covers any file Docling fails on, not only videos.
- **Installing whisper in the Docling image, so videos convert.** The operator's decision was to leave videos out. A transcript of a screen recording is not the material this knowledge base is built from, and a new image changes every conversion's identity.
- **Leaving audio out in the same change.** Not asked for. Recorded above as the next candidate.
- **Calling a video `broken`.** It is not damaged, and saying so would be false.

## Consequences

**What re-runs over an existing working directory:**

- **Stage 1 mints a new run.** `DetectedFormat` and `BrokenCheck` both change, and stage 1's implementation version is `corpus`'s (ADR-058). The new run writes `out-of-scope` for the 8 videos. Nothing else on this archive changes its detected format: the CorelDRAW `RIFF` files stay `UNRECOGNISED`.
- **Stage 2 mints a new run**, because `extraction`'s table gains a row and its upstream run is new. **What was already converted is not converted again.** The extraction cache is keyed on the content hash and the extractor identity, and neither changes, so the 2,492 occurrences stage 2 wrote on 2026-09-28 are read back from it rather than sent.
- **Every later stage mints a new run**, as ADR-167 recorded. Content-keyed caches are reused; run-keyed results are recomputed. On this archive no stage after stage 2 has run yet.

**The removal is visible.** The verdict, its reason and the format-mix page all say so. A video an operator wanted is removed with the rest. What brings one back is the configurable list ADR-146 deferred, or this record being reversed once #326 has landed.

**An audio file can still stop stage 2** the way the MP4s did, on an archive that holds one. This archive's survey found none it could name, but no signature for audio was looked for.

**`CONTEXT.md`'s entry for *Out of scope* names videos beside spreadsheets and BMP images.**

## Tests

Red until the change lands, except the two that pin what must not change and pass today (`aStillImageOrAnAudioFileInTheSameContainerIsNotAVideo`, `anFtypBoxTheBytesCannotHoldIsNotAVideo`). They name the new value by its string, so they compile before it exists.

- `VideoDetectionTest` (in `corpus`), one method per rule:
  - `anIsoBaseMediaFileIsAVideoWhateverItIsCalled`: an `mp42` file with the 24-byte box of the archive's seven, an `isom` file, a QuickTime `qt  ` file and a `3gp4` file, each named as something else, are `VIDEO`, not broken, with no subtype. An `M4V ` file listing `M4A ` among its compatible brands is `VIDEO`.
  - `aStillImageOrAnAudioFileInTheSameContainerIsNotAVideo`: HEIC, AVIF, an image sequence whose only image brand is the compatible `msf1`, a CR3, and an `M4A ` file stay `UNRECOGNISED`.
  - `anFtypBoxTheBytesCannotHoldIsNotAVideo`: `ftyp` at offset 4 of a text file is text, and a binary one whose box size is 8 is `UNRECOGNISED`.
  - `aQuickTimeFileWithNoFtypIsAVideoAndTextThatLooksLikeOneIsNot`: files opening with a `wide`, `moov` or `pnot` atom are `VIDEO`; one opening with `free` is `UNRECOGNISED`; *"The wide range…"* and *"Get free…"* are text.
  - `matroskaAndWebmAreVideos`, `anAviIsAVideoAndNoOtherRiffIs` (CorelDRAW `CDRC` and `WAVE` stay `UNRECOGNISED`, WEBP stays `IMAGE`), `anAsfFileIsAVideo`, `anFlvFileIsAVideo`, `mpegProgramAndElementaryStreamsAreVideos`, `realMediaAndMxfAreVideosAndTextOpeningLikeRealMediaIsNot`.
  - `aTransportStreamIsAVideoOnlyWithThreeSyncsAndNoText`: 188- and 192-byte packet streams are `VIDEO`; text with `G` at 0, 188 and 376 is text; a GIF with `47` at 188 and 376 is `IMAGE`; a binary with only two syncs, or too short for the third, is `UNRECOGNISED`.
  - `anOggFileIsAVideoOnlyWhenItCarriesTheora`: Theora is `VIDEO`; Vorbis and Opus stay `UNRECOGNISED`.
- `ByteLevelReductionTaskletTest.removesVideosAsOutOfScope`: in stage 1, two byte-identical MP4s are recorded as `VIDEO` and each removed as `OUT_OF_SCOPE` on its own account, with the reason exactly *"a video, and videos are out of scope"*, and neither is hashed. A PNG and a CorelDRAW drawing carry no verdict. The page counts two files left out as out of scope, gives *"Videos"* a row holding 2, and says *"Spreadsheets, BMP images and videos are out of scope, whatever they hold."*
- `DoclingClientTest.postsAVideoUnderTheNameAnUnrecognisedFileIsPosted`: `VIDEO` is posted as `document.bin`, the name `UNRECOGNISED` is posted as, and the options the identity is built from still say `naming=1`.
