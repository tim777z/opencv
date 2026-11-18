# Bundled and managed third-party components (`3rdparty/`)

This manifest documents third-party components shipped in or managed by the
`3rdparty/` directory of this OpenCV 3.0-dev tree. It is intended to improve
supply-chain visibility and make dependency updates/audits easier to plan.

**Sources of truth:** vendored headers/scripts in this directory and
[`3rdparty/readme.txt`](readme.txt). Where exact upstream patch-level metadata
is not embedded in-tree, the entry notes what can be verified locally.

## Vendored libraries (source included in-tree)

| Component | Version (as detected in-tree) | Location | Notes / upstream |
| --- | --- | --- | --- |
| zlib | 1.2.8 (`ZLIB_VERSION`) | `3rdparty/zlib/` | General-purpose compression. Upstream: https://zlib.net/ . Used by image codecs (e.g. PNG/TIFF) when `BUILD_ZLIB=ON`. |
| libpng | 1.5.12 (`PNG_LIBPNG_VER_STRING`) | `3rdparty/libpng/` | PNG codec. Upstream: http://www.libpng.org/ . Controlled by `WITH_PNG` / `BUILD_PNG`. |
| libjpeg | 9.0 compatibility (`JPEG_LIB_VERSION 90`) | `3rdparty/libjpeg/` | IJG JPEG codec. Upstream: http://www.ijg.org/ . Controlled by `WITH_JPEG` / `BUILD_JPEG`. |
| libtiff | 4.0.2 (`TIFFLIB_VERSION_STR`, `TIFFLIB_VERSION 20120615`) | `3rdparty/libtiff/` | TIFF codec. Upstream: http://www.libtiff.org/ (historical) . Controlled by `WITH_TIFF` / `BUILD_TIFF`. |
| JasPer | 1.900.1 (`JAS_VERSION`) | `3rdparty/libjasper/` | JPEG-2000 support. Upstream: http://www.ece.uvic.ca/~frodo/jasper/ (historical). See `3rdparty/libjasper/` for license files/text. |
| OpenEXR | Vendored source (see `3rdparty/openexr/` headers/configuration, including `IlmImf/ImfVersion.{h,cpp}`) | `3rdparty/openexr/` | HDR image format support. Upstream: http://www.openexr.com/ . Controlled by `WITH_OPENEXR` / `BUILD_OPENEXR`. |
| libwebp | Vendored source (encoder ABI `WEBP_ENCODER_ABI_VERSION 0x0201` in `webp/encode.h`) | `3rdparty/libwebp/` | WebP codec support. Upstream: https://developers.google.com/speed/webp . Controlled by `WITH_WEBP` / `BUILD_WEBP`. |
| Jinja2 | 2.7.1 (`__version__` in `jinja2/__init__.py`) | `3rdparty/jinja2/` | Python templating library vendored for build/documentation tooling; not linked into the core C++ runtime. |

## Downloaded/prebuilt or externally provided components

| Component | Version / package identifier (as configured) | Location / integration | Notes |
| --- | --- | --- | --- |
| Intel IPP ICV (ippicv) | Platform packages configured in downloader: Linux `ippicv_linux_20141027.tgz` (md5 `8b449a536a2157bcad08a2b9f266828b`), macOS `ippicv_macosx_20141027.tgz` (md5 `9662fe0694a67e59491a0dcc82fa26e0`), Windows `ippicv_windows_20141027.zip` (md5 `b59f865d1ba16e8c84124e19d78eec57`) | `3rdparty/ippicv/downloader.cmake` (downloads to `3rdparty/ippicv/downloads/...`, unpacks to `3rdparty/ippicv/unpack/...`) | Used when `WITH_IPP=ON` and ICV is enabled. Treat as a binary dependency: verify hashes, pin/update deliberately, and review license/redistribution terms for target platforms. |
| Intel TBB (build-from-source helper) | Source archives configured in `3rdparty/tbb/CMakeLists.txt`: default `tbb41_20130116oss` (md5 `3809790e1001a1b32d59c9fee590ee85`), Windows ARM path `tbb41_20130613oss` (md5 `108c8c1e481b0aaea61878289eb28b6a`) | `3rdparty/tbb/` (CMake-based download/build helper; no full vendored TBB runtime source committed here) | Used only when `BUILD_TBB=ON` (limited platform support in this tree). Prefer system/provided TBB where possible; update URLs/hashes together when changing versions. |
| FFmpeg / video backends | Prebuilt Windows FFmpeg wrapper binaries and integration scripts; on Unix-like systems OpenCV typically links user/system FFmpeg/libav/GStreamer | `3rdparty/ffmpeg/` (`ffmpeg.cmake`, `readme.txt`, `license.txt`) | Licensing differs by backend/build (LGPL/GPL components possible). See `3rdparty/ffmpeg/readme.txt` and `3rdparty/ffmpeg/license.txt` before redistribution. |

## Compatibility/support headers

| Component | Location | Notes |
| --- | --- | --- |
| DirectShow headers | `3rdparty/include/dshow/` | Windows capture/video support headers used by `videoio`/platform code as needed. |
| OpenCL headers | `3rdparty/include/opencl/` | OpenCL API headers for builds with OpenCL support (`WITH_OPENCL`). |
| MSVC integer type compatibility headers | `3rdparty/include/msc_inttypes.h`, `3rdparty/include/msc_stdint.h` | Toolchain compatibility for older MSVC environments. |

## Update and audit guidance

- **Vendored code:** update by replacing the vendored directory with the chosen
  upstream release, record the exact version and upstream source/tag/commit in
  this file, and rerun relevant configure/build/tests for affected modules.
- **Downloaded binaries/sources (IPPICV/TBB helpers):** update CMake
  version/URL/hash metadata together and verify checksums before enabling in CI
  or release artifacts.
- **Supply-chain checks:** keep GitHub Actions, SBOM, and dependency-audit
  workflows green; when adding scanners or manifest checks, prefer deterministic
  inputs and pinned tool versions.
- **No secrets:** do not add credentials, tokens, or private download URLs to
  this repository or CI configuration.
