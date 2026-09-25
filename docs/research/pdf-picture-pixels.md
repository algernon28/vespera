# What it takes for a PDF's pictures to carry pixels, and which of them are furniture

Research record for [#286](https://github.com/algernon28/vespera/issues/286): whether Docling can be asked for
the pixels of the pictures it finds in a PDF, what asking costs, and whether
[ADR-149](../adr/0149-a-survivors-pictures-reach-its-cluster-file-from-the-extraction-cache-and-a-picture-that-recurs-is-furniture.md)'s
furniture rule still separates furniture from content once it can see them. It records facts only. The decision is
[ADR-150](../adr/0150-a-pdfs-pictures-are-asked-for-as-embedded-pixels-and-a-picture-repeated-at-one-place-or-as-a-near-copy-is-furniture.md).

## Scope and method

Every claim is labelled. **`MEASURED`** means it was produced on 2026-09-25 by the probe described here.
**Source** means it was read from the code inside the pinned image, and the file and line are given. **Extrapolated**
means it is arithmetic over measured numbers, and the inputs are named.

**The sidecar.** A probe container ran the image `compose.yaml` and `vespera.docling.image` pin,
`vespera/docling-serve-cpu-libreoffice:v1.32.0`, with the settings `compose.yaml` gives it (`--init`,
`--cap-drop ALL`, `no-new-privileges`), published on port 5002. Nothing `compose.yaml` runs was started, stopped or
reconfigured. Its `/version` answered `docling-serve 1.32.0`, `docling-jobkit 3.5.0`, `docling 2.124.0`,
`docling-core 2.93.0`, `docling-ibm-models 4.0.1`, `docling-parse 7.16.0`, `cpython-312 (3.12.13)`, the map
ADR-090 composes the identity from. The host was Windows 11 Pro build 10.0.26200, WSL2, Docker 29.6.1, with 16
logical processors and no CPU or memory cap on the container.

**The request.** Python 3.13.5 with `requests` posted `POST /v1/convert/file` in the multipart shape
`DoclingClient.convert` sends: one `files` part named as ADR-100's table names it (`document.pdf` for a PDF,
`document.bin` for an image), `to_formats=json` and `ocr_preset=rapidocr`, plus the option under test. Calls were
made one at a time.

**The fixtures.** Copies of the 12 PDF and image survivors of the 2026-09-24 GesPOS run, listed in
`D:\development\vespera-runs\2026-09-24-lo-gespos\survivors.csv`: 10 PDFs and 2 `.JPG` screenshots. They were
copied read-only from the archive into the session scratchpad, and are numbered here as the probe numbered them:

| # | Survivor | Pages | Bytes |
| --- | --- | --- | --- |
| 01 | `Flussi_Card_M100/GESPOS TELEGESTIONE - SITEBA - Specifiche flusso CARD_ESITI_TLG  Ver_1.pdf` | 6 | 83,430 |
| 02 | `Flussi_Card_M100/GESPOS TELEGESTIONE - SITEBA - Specifiche flusso CARD_RICHIESTE_TLG Ver_1.pdf` | 6 | 82,979 |
| 03 | `GTHF v2.9(Poste).pdf` | 18 | 597,107 |
| 04 | `_OLD_VARIE/Esiti/EsitiM100/STB_SF_011_USIN_SPEC_M100_RISPO_E01_01_2010.pdf` | 8 | 117,166 |
| 05 | `_OLD_VARIE/Esiti/EsitiTGS/EsitiTelegestione_CARD_ESITI_TLG_A1_Simpligi_v1_1.pdf` | 4 | 106,893 |
| 06 | `_OLD_VARIE/Esiti/EsitiTGS/EsitiTelegestione_CARD_ESITI_TLG_v2_1.pdf` | 7 | 125,684 |
| 07 | `_OLD_VARIE/Esiti/EsitiTGS/EsitiTelegestione_CARD_ESITI_TLG_v2_2.pdf` | 8 | 133,052 |
| 08 | `_OLD_VARIE/GESPOS RICHIESTA INTERVENTO - SITEBA - Specifiche flusso CARD_M100GEM Ver_1.pdf` | 6 | 90,751 |
| 09 | `_OLD_VARIE/PAXbyProject/Cattura.JPG` (550×93 px) | 1 | 12,015 |
| 10 | `_OLD_VARIE/PAXbyProject/Cattura2.JPG` (1040×578 px) | 1 | 41,766 |
| 11 | `_OLD_VARIE/PAXbyProject/CreazioneTemplate_PAXStore_1_0.pdf` | 6 | 106,718 |
| 12 | `_OLD_VARIE/PAXbyProject/CreazioneTemplate_PAXStore_1_1.pdf` | 6 | 106,659 |

All paths are under `WSDLGesPOS_1_10/`.

**The Office side.** Office pictures already carry pixels (ADR-149's research, §1). Their furniture numbers here
were read from the same run's `extraction_cache`, opened read-only as `file:…/vespera.db?mode=ro`. Nothing was
written to the run directory or the archive.

**Recurrence and inspection.** "Bytes recur" means identical SHA-256 of the decoded PNG. The difference hash is the
one §6 defines. The pictures described as "a logo" or "a diagram" were written out as files and looked at.

The probe, its outputs and its scripts stayed in the session scratchpad and are not committed.

---

## 1. Which options return a picture's pixels

`MEASURED`, over all 12 fixtures unless the row says otherwise:

| Options added to the client's request | Pictures | With pixels | What a picture's `image` holds |
| --- | --- | --- | --- |
| none: the client as it is | 79 | **0** | no `image` key at all |
| `image_export_mode=embedded` | 79 | **79** | `uri` is `data:image/png;base64,…`, `dpi` 144, `mimetype` `image/png` |
| `image_export_mode=referenced` | 79 | 0 bytes | `uri` is a relative file name, such as `image_000000_6bd67e91….png`. The synchronous answer carries no file |
| `image_export_mode=embedded`, `include_images=false` | 79 | **0** | no `image` key |
| `image_export_mode=embedded`, `images_scale=1.0` | 79 | 79 | as `embedded`, at `dpi` 72 |
| `image_export_mode=embedded`, `do_picture_classification=true` | 79 | 79 | as `embedded`, plus a class label (§8) |
| `images_scale=1.0` alone | 79 | 0 | no `image` key |
| `image_export_mode=embedded`, `images_scale=3.0` | none | none | **HTTP 422**, `images_scale exceeds the configured maximum of 2.0.` |

So **`image_export_mode=embedded` is necessary and sufficient**. The picture count is the same under every mode,
and so are the pictures' bounding boxes, up to Docling's own run-to-run variance (§4). The mode decides only whether
pixels are serialised.

**Why, source.** `docling-jobkit` 3.5.0, `convert/manager.py` lines 1659–1668 in the image: "Which images to generate
is controlled explicitly by include_images … How they are serialized (embedded/referenced/placeholder) is a separate
concern handled at export time via image_export_mode". It sets `generate_picture_images = request.include_images`
and applies `images_scale` when either image option is on. `include_images` defaults to true and `images_scale` to
2.0 (the OpenAPI schema the probe served). **So the sidecar already crops every picture on every call today, and
`placeholder` leaves the crops out of the answer.** That is why the option costs no conversion time (§3).

**The IT fixture.** A one-page PDF generated by a script, showing one line of text and, below it, a 240×160 RGB image
of a bar chart (four coloured bars on two black axes over white), drawn at 288×192 points, Flate-compressed. It is the
fixture `DoclingClientIT.returnsAPicturesPixelsFromAPdf` builds. `MEASURED`: both conversions succeed and carry the
line of text. Both find one picture. Under the default it has no pixels, and under `embedded` its `uri` is a PNG
`data:` URI.

## 2. How much bigger the answer is

`MEASURED`, round 0, answer bytes as received:

| # | Default | `embedded` | Ratio |
| --- | --- | --- | --- |
| 01 | 64,646 | 405,447 | 6.3 |
| 02 | 62,790 | 451,844 | 7.2 |
| 03 | 1,089,980 | 1,266,755 | 1.2 |
| 04 | 192,084 | 447,875 | 2.3 |
| 05 | 54,745 | 266,553 | 4.9 |
| 06 | 167,972 | 497,262 | 3.0 |
| 07 | 214,580 | 572,471 | 2.7 |
| 08 | 140,139 | 527,010 | 3.8 |
| 09 | 1,914 | 81,892 | 42.8 |
| 10 | 5,648 | 286,939 | 50.8 |
| 11 | 136,400 | 302,746 | 2.2 |
| 12 | 141,734 | 308,081 | 2.2 |
| **all 12** | **2,272,632** | **5,414,875** | **2.4** |

The 79 decoded PNGs total 2,350,769 bytes: min 502, median 22,915, p95 71,302, max 210,892. Base64 adds a third
on top. **Over the 10 PDFs alone the answers grow by 2,780,974 bytes, about 278 KB per PDF.**

For scale, `MEASURED` read-only: the GesPOS run's whole `extraction_cache` is 117 rows and 28,045,180 bytes of
`response_json`. Its 16 PDF rows (the 10 survivors, 4 seeds and 2 others) hold 3,785,937 of them.

## 3. What it costs per call

`MEASURED`. Three rounds over the 12 files. Within each round, each file was converted with the default and then
with `embedded`, one after the other, so any drift in the host hits both alike. The sidecar's own reported
`processing_time`, in seconds:

| # | Default: median (min–max) | `embedded`: median (min–max) |
| --- | --- | --- |
| 01 | 6.81 (6.72–7.27) | 6.76 (6.60–7.15) |
| 02 | 7.38 (7.15–7.63) | 7.07 (7.06–7.58) |
| 03 | 60.92 (59.42–72.05) | 59.21 (57.54–70.16) |
| 04 | 16.49 (15.94–17.05) | 16.21 (15.82–16.61) |
| 05 | 7.52 (7.51–7.65) | 7.53 (7.35–8.16) |
| 06 | 14.96 (14.52–15.51) | 14.89 (14.42–15.03) |
| 07 | 15.87 (15.70–16.19) | 15.87 (15.62–16.20) |
| 08 | 10.69 (10.11–11.79) | 10.30 (10.08–11.74) |
| 09 | 0.61 (0.60–0.64) | 0.62 (0.59–0.64) |
| 10 | 0.67 (0.66–0.68) | 0.66 (0.66–0.68) |
| 11 | 14.20 (13.82–14.34) | 13.87 (13.72–14.24) |
| 12 | 13.78 (13.74–17.85) | 13.87 (13.53–23.13) |
| **sum of medians** | **169.9** | **166.9** |

Per round the sums were 174.5, 183.4 and 166.6 s with the default, and 177.4, 179.8 and 164.0 s with `embedded`.
**No difference shows above the variation between rounds.** The round trip seen by the client is the processing time
rounded up to the sidecar's 2-second tick (ADR-140), and it moved with it.

## 4. What else changes in the answer

`MEASURED`, comparing every round of both modes, per file:

- **The text is identical**: every `texts[].text`, in order, in all 12 files, in all six conversions of each.
- **Every table cell's text is identical** in all 12 files.
- **The rest of the table and picture JSON varies run to run, in both modes alike.** In 3 files one conversion
  out of the six differs from the other five in table geometry and picture bounding boxes: file 04 in one `embedded`
  round, 11 in one default round, 12 in one default round. It is Docling's own variance, not the option's.
- **The picture count per file is the same in every recorded conversion.** The one exception is outside the record:
  the probe's first call, a warm-up of file 05 against a freshly started sidecar, found 6 pictures where every later
  call found 7. The GesPOS cache holds 76 pictures for the 10 PDFs where the probe found 77: its conversion of file
  02 found 6, where every probe conversion found 7. So the count can differ by one between conversions of one file,
  and it did so twice among the conversions compared here.

- **Every option in §1 leaves the text alone.** Across every configuration and every round, each file has one
  signature of its text and table-cell text.

**Office formats**, `MEASURED` on three GesPOS survivors converted once with the default and once with `embedded`:

| Survivor | Text items | Pictures | Text equal | Every picture's `uri` equal | Tables equal |
| --- | --- | --- | --- | --- | --- |
| `Docs_da_analizzare/GesposEvoluzioni.docx` | 63 | 5 | yes | yes | yes |
| `_OLD_VARIE/Esiti/EsitiTGS/EsitiTelegestione_CARD_ESITI_TLG_A1_Simpligi_v1_1.docx` | 73 | 10 | yes | yes | yes |
| `Docs_da_analizzare/GesPOS Logical Architectural map.pptx` | 237 | 23 | yes | yes | yes |

**An Office answer is the same under both modes.** Its pictures already carry `data:` URIs by default. Source,
`docling-core` 2.93.0, `types/doc/document.py` lines 3411–3431 in the image: `embedded` turns a picture referenced by
a file URI into a `data:` URI, and leaves one that is already a `data:` URI as it is.

## 5. Resolution, and whether a chart is legible

`MEASURED`. Every picture under `embedded` is declared `dpi` 144. Its pixel size against its bounding box in points
gives 142 to 148 dpi, 144 for 71 of the 79. That is `images_scale` 2.0 times the 72 points of an inch.

The one chart-like picture in the 10 PDFs, an entity-relationship diagram on page 3 of file 03, comes out 1,201×616
pixels and 94,767 bytes. Every label in it reads cleanly: box titles such as "Detail Records", the record codes in red
("2001", "9001/001"), and the cardinalities ("0,n", "1,1"). The logos are sharp. The heading banner of files 06 and 07
("Esiti telegestione CARD_ESITI_TLG") reads cleanly at 446×113.

At `images_scale=1.0` (72 dpi) the same diagram is 601×308 pixels and 37,448 bytes. It is still readable, but its
smallest labels ("0,1", "1,1") are about 7 pixels high, and the red codes blur. Over the 12 files the pictures total
788,150 bytes, a third of the 2,350,769 at 2.0, and the answers 3,331,263 bytes against 5,414,875. The processing time
does not move: summed over the 12 files, 173.8 s for `images_scale=1.0` alone and 166.5 s with `embedded`, against
174.5 s and 177.4 s at the default scale in round 0. **2.0 is also the most the sidecar accepts**: 3.0 is refused with HTTP 422,
naming "the configured maximum of 2.0" (§1). A higher scale is a server setting, not a request option.

## 6. Recurrence among real crops

`MEASURED` over the 77 PDF pictures under `embedded`, round 0.

### 6a. Bytes

54 distinct byte strings. **43 pictures have bytes that recur**: 41 across documents, 2 within one document only.
Nearly all the cross-document matches are between two versions of one document with the same layout: 05, 06 and 07
(the `EsitiTelegestione` family), 11 and 12 (`CreazioneTemplate` 1_0 and 1_1), and 01, 02 and 08 (the SITEBA
specifications).

**Crops of one logo from different pages of one document mostly differ.** Docling's bounding box for the same logo
moves by a point or two from page to page, so the crop is a pixel or two wider or taller: file 01's header logo comes
out 271×83, 277×89, 276×87, 274×85, 272×83 and 275×85 on its six pages, six different byte strings. **57 pictures sit
at the same place on two or more pages of their document** (§6b), and only 10 of them share their bytes with the crop
at that place on another page. **Crops are deterministic, though**: all 79 pictures came back byte-identical in each
of the three `embedded` rounds.

### 6b. Position

A picture "sits at the same place" as another when both are in one document, on different pages, and each of the
four bounding-box edges differs by at most a tolerance. Pictures matched, by tolerance:

| Tolerance | 0 (exact) | 0.5 pt | 1 pt | 2 pt | 3 pt | 4 pt | 6 pt |
| --- | --- | --- | --- | --- | --- | --- | --- |
| pictures | 0 | 35 | 50 | 57 | 57 | 57 | 57 |

The 57 are the page-header logos of files 01, 02, 04, 05, 06, 07, 08, 11 and 12, and the wide `ACCOR Services`
header banner of files 02 and 08. By inspection every one is furniture. ADR-149's research §5 counted 55 of 141 by
rounded coordinates, over every PDF answer in the cache, the seeds' included. This count is over the 10 PDF
survivors only, with a tolerance.

Among the Office survivors (read-only from the cache), 88 of the 298 cached pictures with pixels carry a bounding box,
because slides have positions. **None of the 26 pictures ADR-149 keeps has another picture within 3 points on another
page of its document.**

### 6c. The difference hash

Defined so that Java's `javax.imageio` and the probe's Python compute the same bits. **They did not, for palette
images, until the definition said how to read one.** The first Java implementation read a palette image's index as
if it were its red sample, so a transparent palette PNG hashed to 0. Over the 127 Office pictures with pixels, it
disagreed with the probe on 26, every one of them a palette PNG. The GesPOS result of §7, 26 Office pictures kept, did
not move: each of the 26 disagreeing pictures has bytes that recur, so it was already furniture by bytes, and no hash
was consulted to decide it. The definition, as corrected:

1. Decode, reading the stored sample values with no colour-space conversion. A palette image is read through its
   palette, as PIL's `convert` does: each pixel is its palette entry's red, green, blue and alpha, the alpha from the
   transparency chunk. A sample wider than 8 bits is read by its high byte, as PIL reads 16-bit RGB. Composite any
   transparency over white, each channel as `(c·a + 255·(255 − a)) / 255`. Luminance is `(299 R + 587 G + 114 B) / 1000`, in integers.
2. A grid of 9 columns by 8 rows. Pixel `(x, y)` belongs to column `x·9/width` and row `y·8/height`, in integer
   division. A cell's value is the integer mean (sum divided by count) of its pixels.
3. Bit `(row, c)` for `c` from 0 to 7 is 1 when cell `c+1` is greater than cell `c`. 64 bits, row by row, the first
   the most significant. An image narrower than 9 or shorter than 8 pixels has no hash.

**Golden values**, computed by the probe's implementation, for images a test can build without a file:

| Image | Hash |
| --- | --- |
| 90×80, grey `x·255/89` (dark to light, left to right) | `0xffffffffffffffff` |
| 90×80, grey `255 − x·255/89` (light to dark) | `0x0000000000000000` |
| 90×80, black where `(x/10)` is even, white where it is odd | `0xaaaaaaaaaaaaaaaa` |
| 90×80 RGBA, opaque black where `x < 45`, transparent black elsewhere | `0x1818181818181818` |
| 90×80, red `(255,0,0)` where `x < 45`, green `(0,255,0)` elsewhere | `0x1818181818181818` |
| 90×80, red where `x < 45`, blue `(0,0,255)` elsewhere | `0x0000000000000000` |
| 90×80 palette PNG, entry 0 red, entry 1 green, entry 0 where `x < 45` | `0x1818181818181818` |
| 90×80 palette PNG with `tRNS`, entry 0 opaque black, entry 1 transparent black, entry 0 where `x < 45` | `0x1818181818181818` |
| 100×50, black, one white column at `x = 11` | `0x0000000000000000` |
| 100×50, black, one white column at `x = 12` | `0x8080808080808080` |
| 9×8, grey `x·30` | `0xffffffffffffffff` |
| 8×8, anything | no hash |

The fourth row is what compositing over white decides: read as opaque, the transparent half is black and the hash is
0. The fifth and sixth are what the luminance weights decide. The two palette rows are the fifth and the fourth again,
stored as indices: read through the palette they hash as their full-colour twins do, where the first implementation,
reading the index, hashed the second to 0. The seventh and eighth are what integer column
assignment decides: column 11 of 100 falls in cell 0 and column 12 in cell 1.

### 6d. How far apart pictures are, in bits

**Same-place pairs.** Each of the 57 same-place pictures against its nearest same-place partner:

| Bits apart | 0 | 1 | 2 | 3 | 4 | 5 | 6 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| pictures | 25 | 9 | 10 | 9 | 3 | 0 | 1 |

**Pictures that are not the same object can be close too.** In the Office survivor
`EsitiTelegestione_CARD_ESITI_TLG_A1_Simpligi_v1_1.docx`, the heading banners are red text in a black box, most of
them 1,028×56 pixels. The hash looks at 9 columns, and the text fills the first one or two, so banners with different
text hash alike:

| Pair | Pixels | Bits apart |
| --- | --- | --- |
| "SIMPLIGI" and "SIMPLIGI" | 1,025×48 and 1,028×56 | 0 |
| "SIMPLIGI" and "51HGT&jm" | 1,028×56 and 1,028×56 | 3 |
| "Procedura GoAnywhere: SwitchFrontEndTSP_movimenti" and `//172.30.125.23/DATABASE/PROD/arch/GESTPOSFTP/ARRIVO/` | 1,028×56 and 1,023×56 | 6 |

Among the PDF pictures, every pair within 2 pixels on each side and at most 10 bits apart was, by inspection, the same
logo or icon. **Small pictures are noisy.** File 05's 23×20 telephone icon is the same icon as the 24×20 one in files
11 and 12 and the 23×22 one in 06 and 07, and it is 7, 9 and 10 bits from them. At that size a grid cell is 2 or 3
pixels across, and a pixel of crop moves several bits. The diagram in file 03 is 19 bits from its nearest other
picture, and file 03's SIA logo 23.

## 7. Candidate rules over the whole tree

`MEASURED`. The population is what ADR-149's rule counts over: every picture of every GesPOS survivor. That is the
127 Office pictures from the cache and the 77 PDF pictures from the probe, 204 in all. The two JPGs are not in it
(§9). The rule terms: **bytes** is ADR-149 (a), **layer** is ADR-149 (b), **near** is "width and height each within
*p* pixels and at most *n* bits apart", and **place** is §6b's 3 points and at most *m* bits apart.

| Rule | Office kept | Office lost against ADR-149's 26 | PDF kept |
| --- | --- | --- | --- |
| bytes + layer (ADR-149 as written) | 26 | none | 34 |
| + place, *m* = 2 | 26 | none | 12 |
| + place, *m* = 8 | 26 | none | 7 |
| + near, *n* = 2, *p* = 2 | 26 | none | 8 |
| + near *n* = 2, *p* = 2, + place *m* = 2 | 26 | none | 4 |
| **+ near *n* = 2, *p* = 2, + place *m* = 8** | **26** | **none** | **3** |
| + near *n* = 2, *p* = 3 or 5, + place *m* = 8 | 26 | none | 3 |
| + near *n* = 0, *p* = 2, + place *m* = 8 | 26 | none | 7 |
| + near *n* = 3, *p* = 2, + place *m* = 8 | **24** | the "SIMPLIGI" 1,028×56 and "51HGT&jm" banners | 3 |
| + near *n* = 4, *p* = 2, + place *m* = 8 | 24 | the same two | 3 |

The row in bold keeps, in the PDFs: file 03's diagram (1,201×616), file 03's title-page SIA logo (276×109, on page 1 of
18 and in no other document), and file 05's 23×20 telephone icon, which §6d shows is the icon files 06, 07, 11 and
12 carry too, 7 to 10 bits away. It is the same in rounds 1 and 2.

What each term adds over bytes + layer + place *m* = 8: near *n* = 2 removes 4 more PDF pictures. They are the
Ticket Restaurant logo on page 1 of file 08 (1 pixel and 2 bits from file 01's), the heading banner shared by
files 06 and 07 (446×113 and 446×112, 1 bit apart), and a 17×21 document icon in file 05 (1 pixel and 2 bits from
file 06's).

## 8. Docling's picture classification

`MEASURED`, one conversion of each of the 12 files with `image_export_mode=embedded` and
`do_picture_classification=true`. Each picture's `meta.classification.predictions` carries a class and a confidence.

**Cost.** 169.0 s of processing summed over the 12, against 166.9 s for the medians of `embedded` alone (§3). The
model ships in the image, and nothing was downloaded. The answers grow a further 7 %, from 5.41 to 5.78 MB.

**On the PDFs it separates furniture from content almost perfectly.** Of the 77 PDF pictures, 65 are labelled
`logo` and 12 `icon`, mostly at a confidence of 0.95 to 1.0. File 03's diagram is `flow_chart` at 0.98, and it is the
only PDF picture labelled anything else. The `ACCOR Services` header banners are `icon` at 0.49 to 0.56. The heading
banner of files 06 and 07 is `logo` at 0.89 and 0.83. File 10's screenshot is `screenshot_from_computer` at 0.78, and
file 09's is `logo` at 0.40.

**On Office pictures it is much less sure.** Three survivors, one conversion each:

| Survivor | Labels, picture by picture |
| --- | --- |
| `GesposEvoluzioni.docx` | `logo` 0.99, then three architecture diagrams as `flow_chart` 0.89 to 0.96, then one picture with no prediction |
| `…_Simpligi_v1_1.docx` | a logo as `logo` 0.96; the eight text banners ADR-149 keeps as `table` 0.38, `bar_chart` 0.38, `logo` 0.50 and 0.55, `icon` 0.26 to 0.33, `photograph` 0.29; one picture with no prediction |
| `GesPOS Logical Architectural map.pptx` | stencil icons as `icon` 0.69 to 0.76 or `logo` 0.69; four pictures as `table` 0.96 |

So a rule that dropped `logo` and `icon` would keep file 03's diagram and drop the title-page logo and the telephone
icon that §7's rule keeps. It would also drop the `.docx` heading banners that ADR-149's inspection called content,
because the classifier calls them logos and icons at under 0.6.

**It is a model judging a picture.** ADR-149 records that the operator ruled a model judging a picture out of scope
in #285.

## 9. Standalone image files

`MEASURED`. Docling routes an image through the same paginated pipeline as a PDF. Each GesPOS JPG comes back as one
page and one picture:

| # | Original | Page | Picture under `embedded` |
| --- | --- | --- | --- |
| 09 | 550×93 px JPEG, 12,015 bytes, 96 dpi | 412.5×69.75 pt | 645×117 px PNG, 59,909 bytes, a region of 65 % of the original's area |
| 10 | 1,040×578 px JPEG, 41,766 bytes, 96 dpi | 780×433.5 pt | 1,158×517 px PNG, 210,892 bytes, a region of 44 % of the original's area |

The picture is a crop of the original re-sampled from 96 to 144 dpi, so one and a half times its pixel resolution,
and re-encoded as PNG. It is about five times the original's bytes. The text under it is OCR, which ADR-145 already
reads.

## 10. Inputs to the re-conversion cost

`MEASURED` read-only from the 2026-09-24 GesPOS run, `vespera.log` and `extraction_cache`:

- **Step durations**: `extraction` 3 min 9.6 s, over walk 1's 129 file occurrences; `seed-extraction` 2 min 29.4 s,
  over walk 2's 19; the whole job 9 min 21.1 s. `embedding-scoring` took 3 min 40 s.
- **Processing time by origin**, the sidecar's own, per cached row:

| Origin | Rows | Mean | Median | Max |
| --- | --- | --- | --- | --- |
| `.docx` | 39 | 2.25 s | 0.97 s | 7.9 s |
| PDF (and the 2 images) | 16 | 19.20 s | 14.76 s | 69.8 s |
| `.doc` through LibreOffice | 1 | 24.56 s | | |
| `.potx` / `.pptx` | 3 | 0.19 s | | |
| Markdown, HTML, none | 58 | 0.01 s | | |
| **all** | **117** | **3.60 s**, 421.0 s in total | | |

- **Which caches the extractor identity keys.** Source, `schema.sql`: `extraction_cache` is keyed by
  `(content_hash, extractor_identity)`. `chunk_cache` is keyed by `(content_hash, chunker_identity,
  chunking_rule_identity, ordinal)` and `vector` by the same plus `embedder_identity`. Neither names the extractor
  identity.

`Extrapolated`: 190 s over 129 walked file occurrences is 1.47 s each, with eight conversions in flight. At that rate
the 42,851-file folder takes about 63,000 s, 17.5 hours. The rate carries GesPOS's format mix, and PDFs take nine
times as long as `.docx` here, so the real figure moves with the folder's share of PDFs.

## What was not measured

- **The 42,851-file folder.** Its format mix, and so its re-conversion time and cache growth, are extrapolated from
  GesPOS.
- **A corpus of charts.** GesPOS's PDFs carry one diagram. How often two different charts share a frame and come
  within 8 bits is unknown. That is the case §7's place term could get wrong.
- **Scanned PDFs.** Every fixture here is a born-digital PDF. A scanned page is one image, and how Docling crops it
  was not measured.
- **Concurrency.** Calls were made one at a time. ADR-140's eight in flight was not re-measured with the larger
  answers.
- **`images_scale` above 2.0**, which needs a server setting the sidecar does not have.
