"""Starts docling-serve with the name of the image it runs added to what /version reports.

docling-serve answers /version with a dict it builds once, at import, and no setting adds a key to it.
So this does what `docling-serve run` does -- it calls the same function -- after adding the one key,
`vespera-image`, from VESPERA_IMAGE, the name the Containerfile baked in (ADR-179 section 2). It must
run in the interpreter that serves, so UVICORN_WORKERS and UVICORN_RELOAD have to stay unset.
"""

import os
import sys


def main() -> None:
    image = os.environ.get("VESPERA_IMAGE", "").strip()
    if not image:
        print(
            "VESPERA_IMAGE is unset or blank: this image does not know its own name, and refuses to start "
            "rather than report none. Build it with --build-arg VESPERA_IMAGE=<repository:tag>.",
            file=sys.stderr,
        )
        sys.exit(1)

    from docling_serve.helper_functions import DOCLING_VERSIONS

    DOCLING_VERSIONS["vespera-image"] = image

    from docling_serve.__main__ import main as serve

    serve()


if __name__ == "__main__":
    main()
