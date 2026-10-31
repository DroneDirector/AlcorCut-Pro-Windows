# The phone app's native code (native/phone, copied from the phone project) built for Windows with MSVC:
# voice isolation (RNNoise, BSD 3-clause), speech-to-text (whisper.cpp, MIT), morph cut (own code) and
# CineForm export (CineForm SDK, Apache 2.0 / MIT).
cmake_minimum_required(VERSION 3.20)
project(alcorcut_phone_native C CXX)
set(CMAKE_CXX_STANDARD 17)
find_package(JNI REQUIRED)
set(P ${CMAKE_CURRENT_SOURCE_DIR}/../phone)
set(COMPAT ${CMAKE_CURRENT_SOURCE_DIR}/compat)
add_compile_definitions(_CRT_SECURE_NO_WARNINGS _USE_MATH_DEFINES)
# PARTS=clang: voice isolation and speech-to-text (with clang-cl: their C uses variable-length arrays);
# PARTS=msvc: morph cut (MSVC, with its OpenMP).
if (NOT DEFINED PARTS)
    set(PARTS clang)
endif ()
if (PARTS STREQUAL "clang")

# Voice isolation.
file(GLOB RNNOISE_SRC ${P}/rnnoise/src/*.c)
add_library(veyrocut_native SHARED ${P}/voice_jni.c ${RNNOISE_SRC})
target_include_directories(veyrocut_native PRIVATE ${COMPAT} ${JNI_INCLUDE_DIRS} ${P}/rnnoise/include ${P}/rnnoise/src)
target_compile_options(veyrocut_native PRIVATE /O2 -w)
target_compile_definitions(veyrocut_native PRIVATE NOMINMAX)

# Speech-to-text.
set(W ${P}/whisper)
add_library(veyrocut_whisper SHARED
    ${P}/whisper_jni.cpp
    ${W}/src/whisper.cpp
    ${W}/ggml/src/ggml.c
    ${W}/ggml/src/ggml-alloc.c
    ${W}/ggml/src/ggml-backend.cpp
    ${W}/ggml/src/ggml-backend-reg.cpp
    ${W}/ggml/src/ggml-quants.c
    ${W}/ggml/src/ggml-threading.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu.c
    ${W}/ggml/src/ggml-cpu/ggml-cpu.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu-aarch64.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu-hbm.cpp
    ${W}/ggml/src/ggml-cpu/ggml-cpu-quants.c
    ${W}/ggml/src/ggml-cpu/ggml-cpu-traits.cpp
    ${W}/ggml/src/ggml-cpu/unary-ops.cpp
    ${W}/ggml/src/ggml-cpu/binary-ops.cpp
)
target_compile_definitions(veyrocut_whisper PRIVATE NOMINMAX GGML_USE_CPU GGML_AVX GGML_AVX2 GGML_FMA GGML_F16C)
target_include_directories(veyrocut_whisper PRIVATE ${COMPAT} ${JNI_INCLUDE_DIRS} ${W} ${W}/src ${W}/include ${W}/ggml/include ${W}/ggml/src ${W}/ggml/src/ggml-cpu)
target_compile_options(veyrocut_whisper PRIVATE /O2 -w /arch:AVX2 -mavx2 -mfma -mf16c /utf-8)

endif ()

if (PARTS STREQUAL "msvc")
# Morph cut and smooth slow motion.
add_library(veyrocut_morph SHARED ${P}/morph/morph.cpp ${P}/morph/morph_jni.cpp)
target_include_directories(veyrocut_morph PRIVATE ${COMPAT} ${JNI_INCLUDE_DIRS} ${P}/morph)
target_compile_options(veyrocut_morph PRIVATE /O2 /W0 /openmp)
target_compile_definitions(veyrocut_morph PRIVATE NOMINMAX)

# CineForm export (CineForm SDK by GoPro, Apache 2.0 / MIT; its SSE code runs as written on PCs).
set(CF ${P}/cineform)
file(GLOB CF_CODEC ${CF}/sdk/Codec/*.c ${CF}/sdk/Codec/*.cpp)
file(GLOB CF_ENC ${CF}/sdk/EncoderSDK/*.cpp ${CF}/sdk/Common/*.cpp)
add_library(veyrocut_cineform SHARED ${CF}/cineform_jni.cpp ${CF}/cf_mov.cpp ${CMAKE_CURRENT_SOURCE_DIR}/cf/cf_files.cpp ${CF_CODEC} ${CF_ENC})
target_include_directories(veyrocut_cineform PRIVATE ${JNI_INCLUDE_DIRS} ${CF} ${CF}/sdk/Common ${CF}/sdk/Codec ${CF}/sdk/EncoderSDK)
target_compile_definitions(veyrocut_cineform PRIVATE _ALLOCATOR=1 _ASMOPT=0 NDEBUG)
target_compile_options(veyrocut_cineform PRIVATE /O2 /W0 /EHsc)
set_source_files_properties(${CF}/cf_mov.cpp PROPERTIES COMPILE_OPTIONS "/FI${CMAKE_CURRENT_SOURCE_DIR}/cf/cf_pre.h" INCLUDE_DIRECTORIES "${CMAKE_CURRENT_SOURCE_DIR}/cf")
endif ()
