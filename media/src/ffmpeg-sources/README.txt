goldberry-media @VERSION@: the FFmpeg corresponding source
==========================================================

This jar is the complete corresponding source of the FFmpeg libraries that
goldberry-media @VERSION@ publishes as its `ffmpeg-<target>` classifier jars,
from the same repository and under the same coordinates:

  io.github.digitalsmile:goldberry-media:@VERSION@:ffmpeg-sources

Binaries it corresponds to: @TARGETS@.

FFmpeg is licensed under the GNU Lesser General Public License version 2.1 or
later, and this jar is published so that every distribution of its object
code is accompanied by its source, as section 4 of that licence asks
(Goldberry ADR-0508). dav1d is BSD-2-Clause and needs no such thing; it is
here because it is linked statically into libavcodec, which makes it part of
what the binaries were built from.


What is in it
-------------

  @FFMPEG_DIR@/       FFmpeg at tag @FFMPEG_TAG@, commit @FFMPEG_COMMIT@,
                      exactly as `git archive` writes it, plus one file: VERSION
  @DAV1D_DIR@/        dav1d at tag @DAV1D_TAG@, commit @DAV1D_COMMIT@,
                      exactly as `git archive` writes it
  recipe/             the build that turned those trees into the binaries:
    media/src/main/cmake/     the media superbuild (CMakeLists.txt) and the
                              scripts and probe it runs
    gradle/libs.versions.toml where it reads the tags and commits from
  notices/<target>/   each published binary jar's ffmpeg-NOTICE.txt, with the
                      exact configure line that target was built with
  licenses/           FFmpeg's and dav1d's licence texts
  META-INF/           Goldberry's LICENSE, NOTICE and third-party notices

The trees were taken from https://git.ffmpeg.org/ffmpeg.git and
https://code.videolan.org/videolan/dav1d.git. The build refuses a checkout
whose tag no longer names the commit above, and so did the task that made
this jar, so the two are the same source. FFmpeg's own licence texts are
@FFMPEG_DIR@/COPYING.LGPLv2.1 and @FFMPEG_DIR@/LICENSE.md, dav1d's is
@DAV1D_DIR@/COPYING. The recipe is Goldberry's, under the Apache License 2.0
(META-INF/LICENSE).

VERSION holds `@FFMPEG_TAG@`. FFmpeg's build names its version from git when
it can, and from a VERSION file when there is no .git -- which is why FFmpeg's
own release tarballs carry one. With it, a build of this tree reports the
version the published one does.


Rebuilding
----------

You need CMake 3.28 or later, Ninja, meson, pkg-config, make, a C compiler and,
on x64, nasm. On Windows, FFmpeg's configure runs under MSYS2's sh with MSVC
as the compiler.

Unpack with a tool that keeps file modes -- `unzip`, not `jar xf` -- because
FFmpeg's configure is a script that runs others. Then, for linux-x64:

  unzip goldberry-media-@VERSION@-ffmpeg-sources.jar -d src && cd src
  cmake -S recipe/media/src/main/cmake -B build -G Ninja \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$PWD/out" \
        -DGOLDBERRY_TARGET_ID=linux-x64 \
        -DGOLDBERRY_FFMPEG_SOURCE_DIR="$PWD/@FFMPEG_DIR@" \
        -DGOLDBERRY_DAV1D_SOURCE_DIR="$PWD/@DAV1D_DIR@"
  cmake --build build --target install

Nothing is downloaded. out/lib then holds what the `ffmpeg-linux-x64` jar
carries under io/github/digitalsmile/goldberry/media/natives/linux-x64/: the
five libraries, ffmpeg-layout.properties (the struct layout Goldberry reads
them by), and ffmpeg-NOTICE.txt. Another target takes its own
GOLDBERRY_TARGET_ID on a machine of that platform: windows-x64, macos-aarch64
or linux-aarch64. The hardware decoders follow the CMake default, which is
what the published builds used: off on Linux, on on macOS and Windows
(-DGOLDBERRY_MEDIA_HWACCEL=ON|OFF).

The configure line is GOLDBERRY_FFMPEG_CONFIGURE in CMakeLists.txt and the
per-platform additions below it; notices/<target>/ffmpeg-NOTICE.txt quotes the
line each published target was built with. Your build will not be byte for
byte the published one: FFmpeg records its configure command, install prefix
included, in libavutil, the build directories leave their mark, and dav1d
names its version `@DAV1D_TAG@` rather than `@DAV1D_TAG@-0-g...` without a .git.
ffmpeg-layout.properties, which is what Goldberry reads the libraries by,
comes out the same.

To build your own changes, edit the trees and build them the same way.


Relinking
---------

Goldberry loads FFmpeg dynamically and lets you replace it. Point the JVM at a
directory that holds the five libraries and the ffmpeg-layout.properties your
build wrote:

  java -Dgoldberry.media.libdir=/path/to/out/lib ...

A replacement must keep the major versions these libraries have -- the
numbers in their file names -- and the names themselves, which come from
--build-suffix=-goldberry; Goldberry checks the majors and the layout at
start-up and refuses a set that does not match, saying why.
