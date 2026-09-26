# syntax=docker/dockerfile:1

# Reproducible build/test environment for this repository.
#
#   docker build -t opencv-dev .
#   docker run --rm opencv-dev                      # configure + build + test
#   docker run --rm opencv-dev ctest --test-dir /src/build --output-on-failure
#   docker run --rm -it opencv-dev bash             # interactive shell
#
# The CMake flags below are intentionally identical to .github/workflows/ci.yml,
# so a green container build and a green CI run exercise the same code paths.

# ---------------------------------------------------------------------------
# Stage 1: toolchain only. Kept separate so that dependency changes invalidate
# the least amount of work and the toolchain can be rebuilt/reused on its own.
# ---------------------------------------------------------------------------
FROM ubuntu:24.04 AS toolchain

ARG DEBIAN_FRONTEND=noninteractive

# No GUI/media backends are installed: WITH_GTK/WITH_FFMPEG/WITH_CUDA are off, so
# the image only carries what the build and the accuracy tests need. Fewer
# packages also means a smaller CVE surface for the image itself.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        build-essential \
        ca-certificates \
        ccache \
        cmake \
        ninja-build \
        pkg-config \
        python3 \
    && rm -rf /var/lib/apt/lists/*

ENV CCACHE_DIR=/ccache

# ---------------------------------------------------------------------------
# Stage 2: source + build. `docker run` on this image runs the test suite.
# ---------------------------------------------------------------------------
FROM toolchain AS dev

# Parallelism for `cmake --build`. Override: docker build --build-arg BUILD_JOBS=8
ARG BUILD_JOBS=4
# Point at a checkout of https://github.com/Itseez/opencv_extra (branch 3.0) to
# let the data-driven accuracy tests find their fixtures. Leave empty to skip.
ARG OPENCV_TEST_DATA_PATH=""

WORKDIR /src
COPY . /src

# hermetic: every third-party image codec is built from the bundled 3rdparty
# sources, so the result does not depend on the distribution's package versions.
RUN cmake -S /src -B /src/build -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_C_COMPILER_LAUNCHER=ccache \
        -DCMAKE_CXX_COMPILER_LAUNCHER=ccache \
        -DOPENCV_TEST_DATA_PATH="${OPENCV_TEST_DATA_PATH}" \
        -DBUILD_TESTS=ON \
        -DBUILD_PERF_TESTS=OFF \
        -DBUILD_EXAMPLES=OFF \
        -DBUILD_DOCS=OFF \
        -DBUILD_opencv_apps=OFF \
        -DBUILD_opencv_java=OFF \
        -DBUILD_opencv_python2=OFF \
        -DBUILD_opencv_python3=OFF \
        -DBUILD_opencv_viz=OFF \
        -DBUILD_opencv_videostab=OFF \
        -DBUILD_opencv_superres=OFF \
        -DBUILD_opencv_cudaarithm=OFF \
        -DBUILD_opencv_cudabgsegm=OFF \
        -DBUILD_opencv_cudacodec=OFF \
        -DBUILD_opencv_cudafeatures2d=OFF \
        -DBUILD_opencv_cudafilters=OFF \
        -DBUILD_opencv_cudaimgproc=OFF \
        -DBUILD_opencv_cudalegacy=OFF \
        -DBUILD_opencv_cudaobjdetect=OFF \
        -DBUILD_opencv_cudaoptflow=OFF \
        -DBUILD_opencv_cudastereo=OFF \
        -DBUILD_opencv_cudawarping=OFF \
        -DBUILD_opencv_cudev=OFF \
        -DBUILD_ZLIB=ON \
        -DBUILD_PNG=ON \
        -DBUILD_JPEG=ON \
        -DBUILD_TIFF=ON \
        -DBUILD_OPENEXR=ON \
        -DWITH_1394=OFF \
        -DWITH_CUDA=OFF \
        -DWITH_EIGEN=OFF \
        -DWITH_FFMPEG=OFF \
        -DWITH_GSTREAMER=OFF \
        -DWITH_GTK=OFF \
        -DWITH_IPP=OFF \
        -DWITH_JASPER=OFF \
        -DWITH_OPENCL=OFF \
        -DWITH_TBB=OFF \
        -DWITH_V4L=OFF \
        -DWITH_WEBP=OFF \
    && cmake --build /src/build --parallel "${BUILD_JOBS}"

# The accuracy test binaries read the fixture location from the environment at
# run time, so it has to be exported as well as passed to CMake.
ENV OPENCV_TEST_DATA_PATH=${OPENCV_TEST_DATA_PATH}

# ctest is the documented entry point (see README.md "Running the tests").
CMD ["ctest", "--test-dir", "/src/build", "--output-on-failure", "--timeout", "900"]
