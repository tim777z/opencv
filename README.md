### OpenCV: Open Source Computer Vision Library

[![Gittip](http://img.shields.io/gittip/OpenCV.png)](https://www.gittip.com/OpenCV/)

This repository is a mirror of the OpenCV 3.0 development tree (`CV_VERSION 3.0.0-dev`).
It builds the C++ library, the optional bindings and the accuracy test suite with CMake.

#### Resources

* Homepage: <http://opencv.org>
* Docs: <http://docs.opencv.org/master/>
* Q&A forum: <http://answers.opencv.org>
* Issue tracking: <https://github.com/Itseez/opencv/issues>

#### Contributing

Please read before starting work on a pull request: <https://github.com/Itseez/opencv/wiki/How_to_contribute>

Summary of guidelines:

* One pull request per issue;
* Choose the right base branch;
* Include tests and documentation;
* Clean up "oops" commits before submitting;
* Follow the coding style guide.

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
| ----------- | ------- | ----- |
| CMake | >= 3.1 | `cmake_minimum_required` in the top-level `CMakeLists.txt` |
| C++ compiler | C++11 | GCC >= 4.8, Clang >= 3.4, MSVC >= 2015 |
| Python | 2.7 or 3.4+ | only for the Python bindings and the binding generators |
| JDK + Ant | 1.7+ | only for the Java bindings and the Java test suite |

No third-party image libraries are required: the build compiles the bundled `zlib`,
`libpng`, `libjpeg`, `libtiff` and OpenEXR sources from `3rdparty/`.

### Build

Always configure into a separate build directory; in-tree builds are not supported
and leave generated files all over the source tree.

```bash
mkdir build && cd build
cmake ..
make -j$(nproc)
```

Equivalently, with Ninja and explicit parallelism:

```bash
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build build --parallel $(nproc)
```

For a fast, deterministic developer/CI build that skips the GPU, GUI, codec-backend
and binding modules, use the flag set in [`Dockerfile`](Dockerfile) /
[`.github/workflows/ci.yml`](.github/workflows/ci.yml). The essential part is:

```bash
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release \
      -DBUILD_TESTS=ON -DBUILD_PERF_TESTS=OFF -DBUILD_EXAMPLES=OFF \
      -DBUILD_opencv_java=OFF -DBUILD_opencv_python2=OFF -DBUILD_opencv_python3=OFF \
      -DBUILD_opencv_viz=OFF -DWITH_CUDA=OFF -DWITH_OPENCL=OFF -DWITH_FFMPEG=OFF \
      -DWITH_GTK=OFF -DWITH_IPP=OFF
cmake --build build --parallel $(nproc)
```

Module selection works through the per-module options (`-DBUILD_opencv_<name>=OFF`);
disabling a module also disables everything that depends on it. Two traps worth
knowing: `modules/viz` needs a GUI/graphics backend, `modules/videoio` needs a
system video backend (FFmpeg/GStreamer), and the internal test-support module
`opencv_ts` links `highgui` - so disabling `highgui`, `videoio` or `imgcodecs`
silently removes the whole accuracy test suite, not just one module's.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the module layout and
dependency graph.

### Test

The accuracy and regression tests read their fixtures from a checkout of
[opencv_extra](https://github.com/opencv/opencv_extra) (branch `3.0` for this
tree). Without it, data-driven tests report `FAIL_INVALID_TEST_DATA` instead of
passing, so set it up once:

```bash
git clone -b 3.0 --depth 1 https://github.com/opencv/opencv_extra ../opencv_extra
```

Then configure with the data path and run the suite:

```bash
cmake -S . -B build -DOPENCV_TEST_DATA_PATH="$PWD/../opencv_extra" -DBUILD_TESTS=ON
cmake --build build --parallel $(nproc)
ctest --test-dir build --output-on-failure
```

Useful variations:

```bash
ctest --test-dir build --output-on-failure -R video        # one module
ctest --test-dir build --output-on-failure -j4             # parallel
./bin/opencv_test_core --gtest_filter=Mat.*               # one suite, directly
./bin/opencv_perf_core --gtest_filter=Mat                  # performance tests
```

Details, including how to run the Java binding tests, are in
[`docs/TESTING.md`](docs/TESTING.md).

### Environment variables

Build-time (read by CMake; see `git grep '\$ENV{' -- '*.cmake'` for the full list):

| Variable | Used for |
| -------- | -------- |
| `OPENCV_TEST_DATA_PATH` | Fixture directory handed to the test binaries |
| `OPENCV_IPP_PATH`, `IPP_ASYNC_ROOT` | Intel IPP / IPP Async locations (when `WITH_IPP=ON`) |
| `EIGEN_ROOT` | Eigen headers (when `WITH_EIGEN=ON`) |
| `NVSDKCOMPUTE_ROOT` | CUDA toolkit (when `WITH_CUDA=ON`) |
| `ANT_DIR` | Ant installation for the Java bindings |
| `OPENCV_FFMPEG_URL`, `OPENCV_ICV_URL` | Override the prebuilt-binary download URLs |
| `CCACHE_DIR` | Compiler cache location when using `ccache` as compiler launcher |

Build-time knobs used by the container workflow (see [`.env.example`](.env.example)):
`BUILD_JOBS`, `OPENCV_TEST_DATA_PATH`. There are no credentials in this build;
`.env` is git-ignored and `.env.example` is committed.

### Container

```bash
docker build -t opencv-dev .                    # toolchain + build
docker run --rm opencv-dev                      # run ctest inside the image
docker run --rm -it opencv-dev bash             # interactive shell
```

or with compose, which mounts the working tree and keeps `build/` and the ccache
in named volumes so rebuilds are incremental:

```bash
cp .env.example .env
docker compose build
docker compose run --rm opencv                  # configure + build + test
```

### Troubleshooting

* **`make: *** No rule to make target` / missing `opencv_test_*` targets** - the
  test executables only exist with `BUILD_TESTS=ON`, and every module's tests
  require `opencv_imgcodecs` and `opencv_videoio` to be enabled.
* **Tests fail with `FAIL_INVALID_TEST_DATA`** - `OPENCV_TEST_DATA_PATH` is unset
  or points at the wrong `opencv_extra` branch (`3.0` for this tree).
* **`cv::Exception` from `Video`/codec tests** - no video backend was detected;
  install FFmpeg or configure with `WITH_FFMPEG=OFF` and skip those tests.
* **Stale configuration after switching options** - CMake caches everything, so
  remove `build/` and reconfigure rather than toggling flags in place.
* **Compilation of `modules/viz` fails** - it needs a GUI/graphics backend
  (GTK or Qt); configure with `-DBUILD_opencv_viz=OFF`.
