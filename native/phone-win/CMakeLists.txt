# The phone app's native code (native/phone, copied from the phone project) built for Windows with MSVC:
# voice isolation (RNNoise, BSD 3-clause), speech-to-text (whisper.cpp, MIT) and morph cut (own code).
cmake_minimum_required(VERSION 3.20)
project(alcorcut_phone_native C CXX)
set(CMAKE_CXX_STANDARD 17)
find_package(JNI REQUIRED)
set(P ${CMAKE_CURRENT_SOURCE_DIR}/../phone)
set(COMPAT ${CMAKE_CURRENT_SOURCE_DIR}/compat)
add_compile_definitions(_CRT_SECURE_NO_WARNINGS NOMINMAX)

# Voice isolation.
file(GLOB RNNOISE_SRC ${P}/rnnoise/src/*.c)
add_library(veyrocut_native SHARED ${P}/voice_jni.c ${RNNOISE_SRC})
target_include_directories(veyrocut_native PRIVATE ${COMPAT} ${JNI_INCLUDE_DIRS} ${P}/rnnoise/include ${P}/rnnoise/src)
target_compile_options(veyrocut_native PRIVATE /O2 /W0)

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
target_compile_definitions(veyrocut_whisper PRIVATE GGML_USE_CPU GGML_AVX GGML_AVX2 GGML_FMA GGML_F16C)
target_include_directories(veyrocut_whisper PRIVATE ${COMPAT} ${JNI_INCLUDE_DIRS} ${W} ${W}/src ${W}/include ${W}/ggml/include ${W}/ggml/src ${W}/ggml/src/ggml-cpu)
target_compile_options(veyrocut_whisper PRIVATE /O2 /W0 /arch:AVX2 /utf-8)

# Morph cut and smooth slow motion.
add_library(veyrocut_morph SHARED ${P}/morph/morph.cpp ${P}/morph/morph_jni.cpp)
target_include_directories(veyrocut_morph PRIVATE ${COMPAT} ${JNI_INCLUDE_DIRS} ${P}/morph)
target_compile_options(veyrocut_morph PRIVATE /O2 /W0 /openmp)
