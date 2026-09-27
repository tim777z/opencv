# Security Policy

## Reporting a vulnerability

Please report suspected vulnerabilities privately rather than in a public issue.
Use GitHub's **Security advisory - Report a vulnerability** form on this
repository (`Security` tab - `Report a vulnerability`). Do not open a public
pull request or issue for an unfixed vulnerability.

Please include: affected module and version (`3.0.0-dev`, `git rev-parse HEAD`),
the platform, a minimal reproducer, and the impact (crash, memory corruption,
out-of-bounds read/write, file-write, code execution, DoS).

Target response times:

| Stage | Target |
| ----- | ------ |
| Acknowledgement | 3 working days |
| Triage and impact assessment | 10 working days |
| Fix or mitigation plan | 20 working days |
| Public disclosure | after a fix is released, or 90 days, whichever comes first |

Disclosure is coordinated with the reporter. A CVE is requested for issues that
affect users of the released library; everything is credited to the reporter
unless anonymity is requested.

## Supported versions

This repository tracks the OpenCV 3.0 development line. Fixes land on the
default branch (`master`); backports to other branches are not provided.

| Version | Supported |
| ------- | --------- |
| `3.0.0-dev` (`master`) | yes |
| any other branch/tag | no - upgrade to `master` |

## Hardening notes for users and reviewers

OpenCV parses attacker-controlled data in several places. When you review or
patch code in these areas, treat every length, offset and count read from a file
as hostile:

* `modules/imgcodecs/src/grfmt_*.cpp` - image header parsers (BMP, GIF, JPEG,
  PNG, TIFF, RAS, PXM, ...). Header fields drive allocations and loop bounds.
* `modules/imgcodecs/src/bitstrm.cpp`, `loadsave.cpp` - stream reads.
* `modules/core/src/persistence.cpp` - the XML/YAML reader behind
  `cv::FileStorage`. Never feed it a file from an untrusted source, and prefer
  `FileStorage` in read mode only.
* `modules/videoio/src/cap_*.cpp` - container/stream parsers for FFmpeg, GStreamer
  and image sequences.
* `modules/core/src/*.cpp` - `Mat` indexing, ROI/submatrix arithmetic, and the
  resize/remap geometry code; sizes come from the caller and are easy to
  overflow.

Rules the codebase follows, which new code must follow too:

1. **Validate at the boundary.** Public entry points check types, sizes, channel
   counts and non-negativity with `CV_Assert`/`CV_Error` and throw
   `cv::Exception`; they never trust a size that came from a file or a pointer
   arithmetic result.
2. **Bounded copies.** Use `memcpy` with a length that has been validated
   against both buffer sizes, or the `cv::`/`std::` helpers that do. No
   unbounded `strcpy`/`sprintf` on data derived from an input file.
3. **Checked arithmetic.** Compute allocation sizes and pixel counts in a type
   that cannot silently wrap (e.g. `size_t`, or `cv::` size helpers) before
   allocating.
4. **No undefined behaviour.** No uninitialised reads, no signed overflow, no
   out-of-bounds access, no reliance on `NULL` checks that do not happen.
5. **Deterministic errors.** Report failures through `CV_Error` with a specific
   `Error::St*` code rather than asserting or aborting, so callers can
   distinguish "bad input" from "out of memory".

## Build and supply chain

* Every third-party dependency is either vendored under `3rdparty/` or pinned
  exactly in a requirements file (`requirements-dev.txt`,
  `modules/python/requirements.txt`); nothing floats on a version range.
* CI (`.github/workflows/ci.yml`) runs with `permissions: contents: read`, so a
  compromised action or dependency cannot push, release or read secrets from a
  job.
* CI installs the toolchain it needs explicitly and audits the pinned Python
  dependencies with `pip-audit --strict` on every pull request.
* `Dockerfile` builds from a pinned base image tag with a minimal package set and
  no credentials; `.dockerignore` keeps `.git`, local `.env` files, keys and
  build trees out of the build context and the image.
* Dependabot (`.github/dependabot.yml`) keeps the actions, container base and
  pinned Python tooling current.
* There are no secrets in this repository. `.env.example` documents the build
  knobs; the real `.env` is git-ignored, and any future credential must come
  from the CI secret store or the environment, never from a committed file.
