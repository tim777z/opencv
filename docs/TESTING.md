# Testing

Two suites exist and they are not interchangeable:

| Suite | Location | Built by | Run with |
| ----- | -------- | -------- | -------- |
| C++ accuracy/regression | `modules/*/test/` | `BUILD_TESTS=ON` | `ctest --test-dir build` |
| C++ performance | `modules/*/perf/` | `BUILD_PERF_TESTS=ON` | `./bin/opencv_perf_<module>` |
| Java binding tests | `modules/*/misc/java/test/` | `BUILD_TESTS=ON` + `BUILD_opencv_java=ON` + Ant | `ant buildAndTest` (Ant project in `build/java/pure_test/.build`) |

## C++ accuracy tests

```bash
cmake -S . -B build -G Ninja -DBUILD_TESTS=ON \
      -DOPENCV_TEST_DATA_PATH="$PWD/../opencv_extra"
cmake --build build --parallel $(nproc)
ctest --test-dir build --output-on-failure
```

`OPENCV_TEST_DATA_PATH` must point at an `opencv_extra` checkout on the branch
that matches this tree (`3.0`):

```bash
git clone -b 3.0 --depth 1 https://github.com/opencv/opencv_extra ../opencv_extra
```

The test binaries read the same name as an environment variable, so you can also
export it at run time:

```bash
export OPENCV_TEST_DATA_PATH=/path/to/opencv_extra
ctest --test-dir build --output-on-failure
```

Selecting work:

```bash
ctest --test-dir build -R opencv_test_video --output-on-failure  # one module
ctest --test-dir build -j4 --output-on-failure                 # parallel
./bin/opencv_test_core --gtest_filter='Mat/*'                  # one suite
./bin/opencv_test_video --gtest_list_tests                     # what is there
```

A new test goes in `modules/<module>/test/test_*.cpp`; CMake globs that
directory, so no build file needs editing. Write the failing test first, then
the fix - `modules/video/test/test_camshift.cpp` (randomised boxes) and
`modules/video/test/test_kalman.cpp` (estimation accuracy over a noise model)
are the templates to copy.

## Java binding tests

`modules/<module>/misc/java/test/*Test.java` are JUnit 3 style tests extending
`org.opencv.test.OpenCVTestCase`. They are copied into the Ant project
(`modules/java/pure_test`) and built by the `opencv_test_java` CMake target,
which additionally requires a JDK, Ant and a Python interpreter for the
generator. Run them from the generated Ant project:

```bash
cmake --build build --target opencv_test_java
cd build/java/pure_test/.build && ant buildAndTest
```

The fixtures (`res/`, `res/raw/`) are copied from `modules/java/common_test`.

Two things to know before adding one:

1. **Only what the generator emits is callable.** `modules/java/generator/gen_java.py`
   turns the C++ headers into the Java API and writes a report
   (`<build>/java/<module>.txt`) listing ported *and skipped* functions; a
   `Ptr<T>`-returning factory, for example, is skipped in this tree. Check the
   generated `org/opencv/<module>/<Module>.java` in the build directory for the
   real signatures instead of guessing - overloads get Java overloads, and
   in/out reference arguments are copied back into the passed object.
2. **A stub is not a test.** `fail("Not yet implemented")` is swallowed by
   `OpenCVTestCase.fail()` (see `passNYI`), so such a method reports success
   while asserting nothing. It now also prints a warning. Replace stubs with
   real assertions, or delete the method when the underlying API no longer
   exists in the C++ library.

Tests for C++ APIs that no longer exist were removed rather than left as
stubs: `segmentMotion`, `updateMotionHistory`, `calcMotionGradient` and
`calcGlobalOrientation` (motion templates, dropped from the public headers in
the 3.x line) and `BackgroundSubtractorMOG` (replaced by `BackgroundSubtractorMOG2`).

## Reporting coverage gaps

`modules/java/check-tests.py` lists the JUnit methods that are still stubs:

```bash
python modules/java/check-tests.py
```

## What CI runs

`.github/workflows/ci.yml` runs, on every push and pull request:

* **build-and-test** - the CMake flag set documented above plus ccache, then
  `ctest --output-on-failure --timeout 900` against an `opencv_extra` clone.
* **lint** - validates `.clang-format`, and checks the repository's own
  YAML/docs files for trailing whitespace, tabs in YAML and invalid YAML.
* **dependency-audit** (pull requests) - `pip-audit --strict` over the pinned
  `requirements-dev.txt`.

A change is expected to keep all three green; a red build is never merged.
