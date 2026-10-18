# What words Docling's OCR returns for a standalone image

Research record for [#556](https://github.com/algernon28/vespera/issues/556), a child of map
[#551](https://github.com/algernon28/vespera/issues/551). It covers what the pinned docling-serve returns, in words, for a
standalone image file converted the way `DoclingClient` converts one, on the CPU image and on the GPU image. It records
facts only. The word floor itself is decided elsewhere.

## Answer in brief

- **A textless image comes back either empty or with one to three stray tokens.** Of 18 textless images (16 public
  samples and 2 drawn ones), 11
  came back with no text at all (`word_count` 0). The other 7 came back with 1 to 3 tokens of one to four characters:
  `0`, `p`, `O`, `@0`, `Q`, `62`, `C000`, and one `—` (U+2014). 6 of those 7 hold a letter or a digit, so they pass
  tier 1 (`UsableText`). Only the `—` fails it.
- **A short real label comes back as 1 word.** `OK`, `Annulla` and `Salva` each give `word_count` 1. A two-caption
  dialog gives 2. So the stray tokens (1 to 3 words) and the labels (1 to 2 words) overlap. No word count separates
  them on this sample.
- **CPU and GPU return the same words.** On all 38 conversions, the text was identical between the two images. Only
  `layout_score`, and so `mean_score`, moved, in the third or fourth decimal place. One picture crop was 1 px wider.
  RapidOCR runs on ONNX Runtime's CPU provider in both images, and only the layout model uses CUDA on the GPU image.
- **The OCR text is never inside the picture item itself.** Text the OCR reads inside a picture comes back as child
  text items of that picture (`parent: #/pictures/N`). Text it reads anywhere else comes back as top-level body text
  items. `DoclingDocumentTexts` reads both, so tier 1 and `word_count` see all OCR text. A photo that fills the frame
  gets no picture item at all, because Docling drops a picture covering more than 90% of the page.
- **`ocr_score` is populated only when an OCR cell survives. `mean_score` is always populated.** An image with no text
  gets `layout_score` 0.0 and `mean_score` 0.0 (grade `poor`), not null. A textless image that keeps a picture item gets
  `mean_score` 0.93 to 0.99 with zero words.
- **`do_ocr` defaults to `true` and `force_ocr` to `false`.** OCR therefore runs only inside the regions the layout model
  detects, never over the whole page.
- **HEIC and AVIF are refused.** Both images answer HTTP 200 with `status: skipped` and `File format not allowed:
  document.bin`.

## Scope and method

Every claim is labelled. **`MEASURED`** means it was produced on 2026-10-18 by the conversions described here. **Source**
means it was read from the code installed in the pinned image, with the file and line given. Each cited file is
byte-identical to the upstream tag linked under Sources. **Inferred** means it follows from the source, or from the measurements, but
was not measured directly.

**Sidecars.** Two throwaway containers ran Vespera's own builds of the pinned images on ports 5002 and 5003:
`vespera/docling-serve-cpu-libreoffice:v1.32.0-docling-parse-7.17.0-r2` (base
`quay.io/docling-project/docling-serve-cpu:v1.32.0`, `docker/docling-serve/Containerfile:17`) and
`vespera/docling-serve-cu128-libreoffice:v1.32.0-docling-parse-7.17.0-r2` (base
`quay.io/docling-project/docling-serve-cu128:v1.32.0`, `compose.gpu.yaml:27`, ADR-170). They ran with `--init`,
`--cap-drop ALL`, `no-new-privileges` and `DOCLING_SERVE_MAX_SYNC_WAIT=600`, and the GPU one also with `--gpus all`
(an RTX 5060 Ti). They were removed afterwards. The operator's running sidecar was not touched. Both answered `/version`
with docling-serve 1.32.0, docling-jobkit 3.5.0, docling 2.124.0, docling-core 2.93.0, docling-ibm-models 4.0.1,
docling-parse 7.17.0 and cpython 3.12.13.

**Packages that differ between the two images** (`importlib.metadata`, `MEASURED`):

| | CPU image | GPU image |
| --- | --- | --- |
| torch | 2.13.0+cpu | 2.11.0+cu128 |
| onnxruntime | 1.29.0, providers `Azure`, `CPU` | 1.29.0, providers `Azure`, `CPU` (no `onnxruntime-gpu`) |
| rapidocr | 3.9.2 | 3.9.2 |
| pillow | 12.3.0 (AVIF built in, no HEIF plugin) | 12.3.0 (AVIF built in, no HEIF plugin) |
| Accelerator device logged | `cpu` | `cuda:0` (layout model, `transformers_engine ... device=cuda:0`) |

**The request.** Each image was sent as `DoclingClient.convert` sends it: `POST /v1/convert/file` with one `files` part
named `document.bin` (`NEUTRAL_EXTENSION`, ADR-100), plus `to_formats=json`, `ocr_preset=rapidocr` and
`image_export_mode=embedded`. Vespera sends exactly those three options, so `do_ocr`, `force_ocr` and `ocr_lang` were
not sent. Calls were made one at a time.

**The images.** No operator file was used, and nothing was downloaded.

- **Public sample images shipped inside the pinned image itself.** These are scikit-image's `skimage/data` set
  (`astronaut`, `brick`, `camera`, `chelsea`, `coffee`, `coins`, `grass`, `gravel`, `hubble_deep_field`, `moon`,
  `rocket`, `text`, and the scikit-image emblem), gradio's `media_assets/images` (`cheetah`, `tower`, the gradio emblem
  and the Hugging Face emoji emblem), and ipywidgets' `jupyter-logo-transparent.png`.
- **Synthetic images drawn with Pillow 12.3.0 and DejaVu Sans inside the pinned image.** These are a blank white page,
  a textless circle-and-triangle mark, grey buttons reading `OK` (at two sizes), `Annulla` and `Salva`, a dialog strip
  with an `OK` and an `Annulla` button, and the word `Benvenuto` at 72 px. Three photos (`rocket`, `chelsea`, `camera`)
  were also pasted onto a white margin so that each covers about 51% of the page.
- **Two format checks.** One was a real AVIF that Pillow encoded from the `OK` button. The other was a HEIC
  *signature only*: an ISO BMFF `ftyp` box with major brand `heic`, compatible brands `mif1 heic`, then padding. No HEIC
  encoder is installed in either image, so no decodable HEIC was tested.

**Counting.** Each response was counted the way Vespera counts one. `DoclingDocumentTexts.parse` walks `body`, then
`furniture`, reads through groups and pictures, and turns each table row into one item. `ExtractedText` joins the items
with a space. `TextMetrics` collapses whitespace runs, splits on spaces, and counts code points that are letters or
digits. Tier 1 asks `UsableText` whether any item outside page headers and footers has a letter or a digit. The counting
was reimplemented in Python, not run through the Java code, so it is **Inferred** equal to `ExtractionMetrics` for these
tokens (the tokens are all ASCII apart from `—`).

## 1. Words per image

`MEASURED`, identical on the CPU and the GPU image unless the last column says otherwise. "Where" is where the text
items sit: **body** means top-level text items, **under picture** means children of a picture item. "Picture" is the
picture item's share of the page area, or `—` where the response has no picture.

| Image | Shows | Text items | `word_count` | Alnum chars | Tier 1 | Where | Picture | `layout` / `ocr` / `mean` | CPU vs GPU |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `blank_white.png` | nothing | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `astronaut.png` | portrait photo | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `coffee.png` | photo | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `hubble_deep_field.jpg` | photo | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `tower.jpg` | photo | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `brick.png`, `grass.png`, `gravel.png` | texture | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `skimage_logo.png` | emblem, no text | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `gradio_logo.png` | emblem, no text | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `logo_shapes.png` | circle and triangle | — | 0 | 0 | fails | — | 66% | 0.927 / null / 0.927 | layout +0.0001 |
| `margin_rocket.png` | photo on margin | — | 0 | 0 | fails | — | 51% | 0.991 / null / 0.991 | same to 4 dp |
| `margin_chelsea.png` | photo on margin | — | 0 | 0 | fails | — | 51% | 0.990 / null / 0.990 | same to 4 dp |
| `cheetah.jpg` | photo | `—` | 1 | 0 | fails | body | — | 0.579 / 0.579 / 0.579 | same |
| `rocket.jpg` | photo | `62` | 1 | 2 | passes | body | — | 0.605 / 0.605 / 0.605 | same |
| `camera.png` | photo | `C000` | 1 | 4 | passes | body | — | 0.745 / 0.745 / 0.745 | same |
| `hf-logo_transpng.png` | emoji emblem | `Q` | 1 | 1 | passes | body | — | 0.934 / 0.934 / 0.934 | same |
| `chelsea.png` | cat photo | `0`, `p` | 2 | 2 | passes | body | — | 0.596 / 0.596 / 0.596 | same |
| `moon.png` | photo | `0`, `0` | 2 | 2 | passes | body | — | 0.554 / 0.554 / 0.554 | same |
| `coins.png` | photo of coins | `O`, `@0`, `O` | 3 | 3 | passes | body | — | 0.729 / 0.729 / 0.729 | same |
| `margin_camera.png` | photo on margin | `C000` | 1 | 4 | passes | under picture | 51% | 0.990 / 0.708 / 0.849 | same to 4 dp |
| `button_ok.png` | button `OK` | `OK` | 1 | 2 | passes | under picture | 31% | 0.939 / 0.999 / 0.969 | picture 30.5%, scores +0.0007 |
| `button_ok_small.png` | 120×48 px button `OK` | `OK` | 1 | 2 | passes | under picture | 38% | 0.913 / 1.000 / 0.956 | crop 121 px wide, not 120 |
| `button_annulla.png` | button `Annulla` | `Annulla` | 1 | 7 | passes | under picture | 30% | 0.879 / 1.000 / 0.940 | layout +0.0008 |
| `button_salva.png` | button `Salva` | `Salva` | 1 | 5 | passes | under picture | 30% | 0.904 / 1.000 / 0.952 | layout 0.909, mean 0.954 |
| `dialog_ok_annulla.png` | two buttons | `OK`, `Annulla` | 2 | 9 | passes | under picture | 12% | 0.929 / 0.999 / 0.964 | layout −0.0002 |
| `word_benvenuto.png` | one 72 px word | `Benvenuto` (`section_header`) | 1 | 9 | passes | body | — | 0.611 / 1.000 / 0.806 | layout +0.0005 |
| `jupyter-logo-transparent.png` | emblem with a word | `jupyter` (`section_header`) | 1 | 7 | passes | body | — | 0.511 / 0.999 / 0.755 | layout +0.0001 |
| `text.png` | handwriting on paper | — | 0 | 0 | fails | — | — | 0.0 / null / 0.0 | same |
| `button_ok.avif` | AVIF | — | — | — | — | — | — | none (`skipped`) | same |
| `header_only.heic` | HEIC signature | — | — | — | — | — | — | none (`skipped`) | same |

Further `MEASURED` facts:

- **Stray tokens are repeatable.** Seven of the images with stray tokens were converted a second time on each image.
  All 14 repeats returned the same tokens and the same scores.
- **The proxies counted beside `word_count`** (`ExtractionMetrics`):
  - `single_character_word_count` is 1 for `—` and `Q`, 2 each for `0 p`, `0 0` and `O @0 O`, and 0 for every real
    label.
  - `vowelless_word_count` is 1 each for `—`, `Q`, `62`, `C000` and `@0`, 2 each for `0 p` and `0 0`, and 0 for every
    real label. `OK`'s `O` is a vowel.
- **Every text item is in the body layer.** None is in a page header or footer, so tier 1's exclusion of headers and
  footers (ADR-232) changes nothing for an image.
- **`origin.mimetype` is `application/pdf`** for every image. `pages` has exactly one page, so `page_count` is 1.
- **Real text can also come back empty.** `text.png`, handwriting photographed at an angle, got no layout region and so
  no OCR at all.

## 2. How a standalone image goes through Docling

Source, docling 2.124.0 and docling-jobkit 3.5.0 as installed in the image.

1. **Format.** The sidecar guesses the format from the first 8,192 bytes with `filetype` (`docling/datamodel/document.py:810-819`).
   `image/png`, `image/jpeg`, `image/tiff`, `image/gif`, `image/bmp` and `image/webp` map to `InputFormat.IMAGE`
   (`docling/datamodel/base_models.py:203-210`). The name `document.bin` plays no part once the bytes give a type.
2. **Backend.** `ImageDocumentBackend` opens the image with Pillow, with no conversion to PDF. Each frame becomes one
   page of size pixels × 72 / dpi (`docling/backend/image_backend.py:179-245`). The page has no text cells
   (`:107-109`), and its whole area is reported as one bitmap (`:111-124`).
3. **Stage order.** The stages run preprocess, then layout, then OCR, then layout postprocessing, then table, then
   assembly (`docling/pipeline/standard_pdf_pipeline.py:771-776`). OCR therefore sees the layout model's raw regions,
   before any confidence threshold has been applied.
4. **Where OCR looks.** With `force_ocr` false, the mode is `OcrMode.DEFAULT`, which runs `PDF_AWARE_LAYOUT_REGIONS`
   (`docling/datamodel/pipeline_options.py:98-113, 201-212`). That mode keeps every layout region that overlaps a
   bitmap (`docling/models/base_ocr_model.py:191-278`). On an image the bitmap is the whole page, so **every raw layout
   region is read, and nothing outside one is**. When the layout model finds no region, no OCR runs and the response is
   empty.
5. **The engine.** The `rapidocr` preset is the `rapidocr` kind with its defaults (`docling_jobkit/convert/manager.py:738-751, 1538-1570`).
   Those defaults are the `onnxruntime` backend, `lang=["chinese"]` (resolved to `ch`, so PP-OCRv6), `text_score` 0.5
   and `scale` 3.0 (`pipeline_options.py:222-234, 290-334`, `docling/models/stages/ocr/rapid_ocr_model.py:32, 299-315`).
   The container log names `PP-OCRv6_det_small.onnx`, `ch_ppocr_mobile_v2.0_cls_mobile.onnx` and
   `PP-OCRv6_rec_small.onnx` (`MEASURED`). On the GPU image docling asks RapidOCR for CUDA
   (`rapid_ocr_model.py:289-296, 418-429`), but the installed ONNX Runtime has no CUDA provider, so OCR runs on the CPU
   (Inferred from the provider list measured above).
6. **Postprocessing.** Layout regions below their label's threshold (0.5 for `text` and `picture`) are dropped
   (`docling/utils/layout_postprocessor.py:185-203, 276-282`). **A `picture` covering more than 90% of the page is
   dropped** (`:345-353`). OCR cells that land in no surviving region become `text` regions of their own, whose
   confidence is the OCR cell's confidence (`:303-321`). `create_orphan_clusters` defaults to true
   (`pipeline_options.py:1491-1500`).
   - This is why a photo that fills its frame has no picture item.
   - It is why its stray tokens sit in the body.
   - It is why `layout_score` equals `ocr_score` on all 7 full-frame images with stray tokens (`MEASURED`).
7. **Assembly.** Text regions nested in a kept picture become text items whose parent is that picture
   (`docling/models/stages/reading_order/readingorder_model.py:90-129`). The picture's pixels are the region cropped
   at `images_scale` 2.0, which is why it is 144 dpi (`MEASURED`: `dpi` 144, `image/png`).

## 3. Text items against the picture cropped from the image

- **The picture item holds no text.** All OCR text is in text items. When the region the text sits in is inside a kept
  picture, those items are the picture's children. Otherwise they are top-level body items (§2.6–7, `MEASURED` in §1).
- **Vespera reads both.** `DoclingDocumentTexts` reads through a picture to the text items under it
  (`src/main/java/io/algernon/vespera/extraction/DoclingDocumentTexts.java:23-26, 85-92`). So tier 1 and `word_count`
  count text under a picture exactly as they count body text.
  - This answers the ticket's "tier 1 reads only text items": the OCR text *is* text items, wherever it sits.
- **The picture is a layout region, not the image.** ADR-150 §4 measured the crop at 44% and 65% of the original's
  area. Here it is:
  - 12% for the dialog strip;
  - 30% to 38% for the buttons;
  - 51% for the photos on a margin;
  - 66% for the drawn mark;
  - absent for every photo that fills its frame, because the 90% rule drops it.
- **Some text cannot be found by any reading.** By §2.4, text lying outside every raw layout region is neither OCR'd
  nor inside the crop, so no reading of the response finds it (Inferred: not measured with text placed deliberately
  outside every region). `text.png` is the measured case of a whole image with readable text and no region.
- **Where a stray token lands depends on the OCR region.** The same `camera` photo gives `C000` in the body at full
  frame and under a picture on a margin. On a margin, `rocket`'s `62` and `chelsea`'s `0 p` disappear (`MEASURED`).
  OCR reads the region at 216 dpi, so a different region size or offset gives a different input.

## 4. `ocr_score` and `mean_score`

Source:
- **`parse_score`** is the 10th percentile of a quality rating of the page's programmatic text cells
  (`docling/models/stages/page_preprocessing/page_preprocessing_model.py:95-110`). An image has none at that stage, so
  it is NaN, serialised as null (`docling/datamodel/service/responses.py:40-62`).
- **`ocr_score`** is set only when at least one OCR cell survives, as the mean of their confidences
  (`base_ocr_model.py:397-401`).
- **`layout_score`** is the mean confidence of the final regions, and **0.0 when there are none**
  (`docling/models/stages/layout/layout_postprocessing_model.py:86-94`).
- **`mean_score`** is the NaN-skipping mean of `ocr_score`, `table_score`, `layout_score` and `parse_score`
  (`docling/datamodel/base_models.py:640-652`). At document level, these are page means (`standard_pdf_pipeline.py:1098-1126`).

`MEASURED`:

| Case | `parse_score` | `ocr_score` | `layout_score` | `mean_score` (grade) |
| --- | --- | --- | --- | --- |
| no text and no picture (11 images, plus `text.png`) | null | null | 0.0 | **0.0** (`poor`) |
| picture kept, no text (`logo_shapes`, two margin photos) | null | null | 0.927–0.991 | 0.927–0.991 (`excellent`) |
| stray tokens, full frame (7 images) | null | 0.554–0.934 | equal to `ocr_score` | 0.554–0.934 (`fair`; `excellent` for `Q` at 0.934) |
| stray token under a picture (`margin_camera`) | null | 0.708 | 0.990 | 0.849 (`good`) |
| button labels | null | 0.999–1.000 | 0.879–0.940 | 0.940–0.969 (`excellent`) |
| one large word, read as `section_header` | null | 0.999–1.000 | 0.511–0.612 | 0.755–0.806 (`fair`/`good`) |

Some of these are Inferred from the table:
- `mean_score` is populated for every converted image. It is 0.0 rather than null when nothing was found.
- It does not track the words: a textless picture scores higher than any real label.
- `ocr_score` separated the labels (≥ 0.999) from the stray tokens (≤ 0.934) on these 26 images. That is a small
  sample.
- The response carries no per-item confidence. `ocr_score` is a document mean, so one real word next to one stray
  token gives a mixed figure.

Vespera stores all six scores in `extraction_metric` (`ExtractionMetrics.java`, insert of `ocr_score`, `mean_score`, …).

## 5. `do_ocr` and `force_ocr` defaults

- **`do_ocr` defaults to `true` and `force_ocr` to `false`.** Source: `docling/datamodel/service/options.py:345-365`, the
  model docling-serve 1.32.0 accepts. The service documentation says the same
  ([usage.md](https://github.com/docling-project/docling-serve/blob/v1.32.0/docs/usage.md), lines 17–18).
  `normalize_convert_options` does not touch either flag (`docling_serve/policy.py:319-339`).
- **`ocr_preset` and `ocr_lang`.** `ocr_preset` defaults to `auto`. Vespera sends `rapidocr`, which gets the defaults
  in §2.5. Vespera does not send `ocr_lang`, so RapidOCR reads with its default `ch` PP-OCRv6 recogniser, which also
  reads Latin script (`OK`, `Annulla`, `Benvenuto` came back exact).
- **What `force_ocr=true` would change.** It would set `OcrMode.FULL_PAGE` (`pipeline_options.py:236-261`), and OCR
  would read the whole page rather than the layout regions (`base_ocr_model.py:161-171`). Not measured.

## 6. HEIC and AVIF

**Not accepted.**

- **`MEASURED` on both images.** HTTP 200, `status: "skipped"`, and one error `{"component_type": "user_input",
  "error_message": "File format not allowed: document.bin"}`. This held for the real AVIF and for the HEIC signature.
- **Source: the type is recognised and then not accepted.**
  - `filetype` 1.2.0 names `image/heic` and `image/avif` from the `ftyp` box
    ([image.py](https://github.com/h2non/filetype.py/blob/v1.2.0/filetype/types/image.py), classes `Heic` and `Avif`).
  - Neither type is in `FormatToMimeType[InputFormat.IMAGE]` (`base_models.py:203-210`), and neither extension is in
    `FormatToExtensions` (`base_models.py:160`).
  - So `_guess_format` returns `None` (`document.py:845-856`).
- **Decoding (Inferred).** Pillow 12.3.0 in both images decodes AVIF, but neither has a HEIF plugin (`pillow-heif` is
  absent). Admitting HEIC would therefore take a code change and a new package, not a setting.
  `image_backend.py:214` mentions HEIC only in a comment.
- **Vespera.** Stage 1 already reads both as `UNRECOGNISED` (`BrokenCheck.STILL_IMAGE_BRANDS`, ADR-168). If one were
  ever sent, the answer would be `skipped`, which `ConversionStatus.SKIPPED` already models.

## 7. What `ExtractionMetrics` would record for a textless image

Inferred from §1 and the code in `ExtractedText`, `TextMetrics` and `UsableText`:

- **No text came back** (11 of 18 textless images): `word_count` 0, `alphanumeric_char_count` 0, `character_count` 0.
  Tier 1 marks it `degenerate-output` with "zero alphanumeric content after whitespace normalisation".
- **Stray tokens came back** (7 of 18): `word_count` 1, 2 or 3, of tokens one to four characters long.
  - 6 of the 7 pass tier 1, because they hold a letter or a digit.
  - `—` alone fails tier 1, yet still has `word_count` 1. `TextMetrics.words` splits on spaces, and does not ask for a
    letter.
- **A short real label**: `word_count` 1 (2 for the two-button dialog), with no single-character or vowelless word.

## What was not measured

- **The operator's images.** Screenshots, scans and photos from the real archive were not used. How often each case
  above occurs there needs a run the operator does.
- **A decodable HEIC**, since no encoder is installed. The refusal comes before decoding, so the outcome is Inferred to
  be the same.
- **Text outside every layout region**, `force_ocr=true`, a different `ocr_lang`, and multi-frame TIFF or GIF.
- **Concurrency.** Calls were one at a time.
- **Other GPU models or drivers.** The GPU findings are from one RTX 5060 Ti under WSL2. The OCR text is Inferred to be
  device-independent, because OCR runs on the CPU in both images.

## Sources

All accessed 2026-10-18. Line numbers are from the copy installed in the pinned image. Every docling, docling-jobkit
and docling-serve file listed was compared byte for byte with the tag linked here, and they are identical.

- docling v2.124.0:
  - [`docling/backend/image_backend.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/backend/image_backend.py)
  - [`docling/models/base_ocr_model.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/models/base_ocr_model.py)
  - [`docling/models/stages/ocr/rapid_ocr_model.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/models/stages/ocr/rapid_ocr_model.py)
  - [`docling/datamodel/pipeline_options.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/pipeline_options.py)
  - [`docling/datamodel/service/options.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/service/options.py)
  - [`docling/datamodel/base_models.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/base_models.py)
  - [`docling/datamodel/document.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/document.py)
  - [`docling/utils/layout_postprocessor.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/utils/layout_postprocessor.py)
  - [`docling/pipeline/standard_pdf_pipeline.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/pipeline/standard_pdf_pipeline.py)
  - [`docling/models/stages/reading_order/readingorder_model.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/models/stages/reading_order/readingorder_model.py)
  - [`docling/models/stages/layout/layout_postprocessing_model.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/models/stages/layout/layout_postprocessing_model.py)
  - [`docling/models/stages/page_preprocessing/page_preprocessing_model.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/models/stages/page_preprocessing/page_preprocessing_model.py)
  - [`docling/datamodel/service/responses.py`](https://github.com/docling-project/docling/blob/v2.124.0/docling/datamodel/service/responses.py)
- docling-jobkit v3.5.0: [`docling_jobkit/convert/manager.py`](https://github.com/docling-project/docling-jobkit/blob/v3.5.0/docling_jobkit/convert/manager.py)
- docling-serve v1.32.0:
  - [`docs/usage.md`](https://github.com/docling-project/docling-serve/blob/v1.32.0/docs/usage.md)
  - [`docling_serve/policy.py`](https://github.com/docling-project/docling-serve/blob/v1.32.0/docling_serve/policy.py)
  - [`docling_serve/settings.py`](https://github.com/docling-project/docling-serve/blob/v1.32.0/docling_serve/settings.py)
- RapidOCR:
  - [documentation](https://rapidai.github.io/RapidOCRDocs/main/install_usage/rapidocr/usage/)
  - [repository](https://github.com/RapidAI/RapidOCR); 3.9.2 is installed in both images.
- filetype.py v1.2.0: [`filetype/types/image.py`](https://github.com/h2non/filetype.py/blob/v1.2.0/filetype/types/image.py)
- scikit-image sample data, the source and licence of each image: [skimage.data](https://scikit-image.org/docs/stable/api/skimage.data.html)
- Vespera at `dc14edb2`:
  - `src/main/java/io/algernon/vespera/extraction/ExtractionMetrics.java`, `ExtractedText.java`, `TextMetrics.java`,
    `UsableText.java`, `DoclingDocumentTexts.java` and `DoclingClient.java`
  - `docker/docling-serve/Containerfile`, `compose.yaml` and `compose.gpu.yaml`
  - ADR-100, ADR-150 §4, ADR-168, ADR-170 and ADR-232
  - [`docs/research/pdf-picture-pixels.md`](pdf-picture-pixels.md) §9
