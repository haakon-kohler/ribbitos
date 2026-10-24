# SPDX-License-Identifier: MIT
# On-device speech-to-text: whisper.cpp (third_party/whisper.cpp, pinned submodule) linked statically
# into one JNI library, libribbit_whisper.so. Only the R1's arm64-v8a is built (app/build.gradle.kts).
cmake_minimum_required(VERSION 3.22.1)
project(ribbit_whisper C CXX)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
# Generic ARMv8 code: build machines aren't the R1, and its Cortex-A53 has no newer extensions.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# ggml's own thread pool, not OpenMP: libomp.so isn't on the device or packaged with the app, so an
# OpenMP build fails to load ("library libomp.so not found", seen on the R1).
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp whisper)

add_library(ribbit_whisper SHARED ribbit_whisper.c)
target_link_libraries(ribbit_whisper PRIVATE whisper log)
