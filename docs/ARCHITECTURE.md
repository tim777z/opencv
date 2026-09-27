# Architecture

This document describes how the tree is organised, how the modules depend on
each other, and which parts are generated. It is the map to consult before
touching anything: most "where does this belong?" questions are answered here.

## Top-level layout

```
3rdparty/        vendored third-party sources (libjpeg, libpng, zlib, libtiff,
                 OpenEXR, protobuf, tbb, qt/...) - built only when the matching
                 BUILD_* option is ON. Never edited by hand.
apps/            command line tools (train_intensity_list, opencv_version, ...)
cmake/           the build system: module machinery, dependency detection,
                 code generation helpers
data/            pretrained models and cascades shipped with the library
doc/             doxygen/plantuml sources for the API documentation
include/         legacy C and C++ headers (opencv/cv.h, opencv2/opencv.hpp)
modules/         the library itself, one directory per module
platforms/       platform glue: android, ios, osx, winrt, build scripts
samples/         example programs (cpp, java, python2, gpu, directx, ...)
```

`modules/` is the only place new library code belongs.

## Module anatomy

Every module follows the same shape:

```
modules/<name>/
  CMakeLists.txt      ocv_define_module(<name> <deps> [WRAP <bindings>])
  include/opencv2/    public headers (the API surface)
  src/                implementation, one file per algorithm family
  test/               accuracy + regression tests  (target opencv_test_<name>)
  perf/               performance tests            (target opencv_perf_<name>)
  misc/               samples, java/python glue, filelists
  doc/                module documentation
```

`CMakeLists.txt` is one line by convention:

```cmake
set(the_description "Video Analysis")
ocv_define_module(video opencv_imgproc WRAP java python)
```

* the dependency list is explicit - CMake disables a module automatically when a
  dependency is missing, so never rely on "it happens to be built";
* `WRAP` marks the module as wrapped by the Java/Python binding generators.

## Module dependency graph

The authoritative source is each module's `CMakeLists.txt`; the table below is
transcribed from them. Layers, from the bottom up: `core` -> primitives ->
algorithms -> applications -> bindings.

| Module | Class | Direct dependencies |
| ------ | ----- | ------------------- |
| `core` | PUBLIC | - (matrix types, linear algebra, persistence, OpenCL/OpenGL primitives) |
| `imgproc` | PUBLIC | `core` |
| `imgcodecs` | PUBLIC | `imgproc` |
| `ml` | PUBLIC | `core` |
| `flann` | PUBLIC | `core` |
| `photo` | PUBLIC | `imgproc` (optional: `cudaarithm`, `cudaimgproc`) |
| `video` | PUBLIC | `imgproc` |
| `videoio` | PUBLIC | `imgproc`, `imgcodecs` |
| `features2d` | PUBLIC | `imgproc`, `ml`, `flann` (optional: `highgui`) |
| `calib3d` | PUBLIC | `imgproc`, `features2d` |
| `objdetect` | PUBLIC | `core`, `imgproc`, `ml` (optional: `highgui`) |
| `shape` | PUBLIC | `core`, `imgproc`, `video` |
| `stitching` | PUBLIC | `imgproc`, `features2d`, `calib3d`, `objdetect`, `imgcodecs` |
| `videostab` | PUBLIC | `imgproc`, `features2d`, `video`, `photo`, `calib3d` |
| `superres` | PUBLIC | `imgproc`, `video` |
| `highgui` | PUBLIC | `imgproc`, `imgcodecs`, `videoio` |
| `viz` | PUBLIC | `core` + VTK |
| `hal` | INTERNAL | - (hardware acceleration shims) |
| `ts` | INTERNAL | `core`, `imgproc`, `imgcodecs`, `videoio`, `highgui` (test support) |
| `cudev` | PUBLIC | - (CUDA runtime wrappers) |
| `cudaarithm` | PUBLIC | `core` (optional: `cudev`) |
| `cudafilters` | PUBLIC | `imgproc`, `cudaarithm` |
| `cuda*` (rest) | PUBLIC | the CPU modules above, plus `cudev`/`cudawarping`/`cudalegacy` |
| `world` | PUBLIC | bundles every public module into a single library |
| `java`, `python2`, `python3` | BINDINGS | `core`, `imgproc` + the wrapped modules |

Two consequences worth memorising:

* `opencv_ts` (internal) links **`highgui`**, and every module's accuracy test
  links `opencv_ts` + `opencv_imgcodecs` + `opencv_videoio`. Turning any of
  those off removes the test suite for *every* module, not just one.
* Optional (`OPTIONAL`) dependencies are probed at configure time; when absent
  the module still builds, with the corresponding algorithms reporting that
  they are unavailable. The configure summary prints the result of every probe.


## Build system

* `CMakeLists.txt` (top level) sets the global options, includes
  `cmake/OpenCV*.cmake` and then `modules/`.
* `cmake/OpenCVModule.cmake` is the engine: `ocv_define_module` globs the
  sources, resolves dependencies, creates the library target and calls
  `ocv_add_accuracy_tests()` / `ocv_add_perf_tests()` / `ocv_add_samples()`.
* `cmake/OpenCVUtils.cmake`, `OpenCVDetect*.cmake`, `OpenCVFind*.cmake` hold the
  dependency detection logic. A new optional dependency needs a `Detect`/`Find`
  module plus a status line, so the configure summary stays truthful.
* Generated files: `opencv2/opencv_modules.hpp` and `cvconfig.h` are written into
  the build tree, never into the source tree.

## Bindings

* `modules/python/src2` - hand written CPython C-API wrappers, packaged by
  `modules/python/setup.py`. Pinned build dependencies: `modules/python/requirements.txt`.
* `modules/java/generator/gen_java.py` - parses the public headers with
  `modules/python/src2/hdr_parser.py` and emits the Java classes plus the JNI
  C++ shim (`<module>.cpp`) into the build tree at build time.
  * Consequence: the Java API mirrors exactly what the generator supports. A
    function returning `Ptr<T>`, for example, is skipped (see the `video.txt`
    generator report in the build directory), and module *classes* are still a
    work in progress in this tree - only the free functions of a module reach
    `org.opencv.<module>.<Module>` reliably. Test accordingly; see
    [docs/TESTING.md](TESTING.md).

## Error handling and validation

There is no logging framework in the library. Contracts are enforced with
`CV_Assert` / `CV_Error`, which throw `cv::Exception`; functions that take sizes,
types or index ranges validate them at the boundary rather than trusting the
caller. This is the pattern to follow in new code, and it is what the security
notes in [SECURITY.md](../SECURITY.md) rely on.

## Where to add what

| Change | Location |
| ------ | -------- |
| New algorithm | existing `modules/<m>/src/*.cpp`, reuse the module's precompiled header |
| New public API | `modules/<m>/include/opencv2/...`, guard it with `CV_EXPORTS_W` |
| Bug fix | add an accuracy test in `modules/<m>/test/` *first*, then fix |
| New dependency | `cmake/OpenCVFind*.cmake` + option + configure status line |
| Performance work | `modules/<m>/perf/`, never inside the accuracy test |
