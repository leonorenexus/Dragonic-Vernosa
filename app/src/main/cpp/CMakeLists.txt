cmake_minimum_required(VERSION 3.22.1)
project(dragonicvernosa)

include(FetchContent)

# Pin ke tag llama.cpp yang stabil. Kalau API berubah & build gagal di Actions,
# cek changelog llama.cpp lalu update tag ini + sesuaikan llama_jni.cpp.
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b9802
)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama_cpp)

add_library(dragonicvernosa SHARED
    llama_jni.cpp
)

target_include_directories(dragonicvernosa PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
    ${llama_cpp_SOURCE_DIR}/common
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(dragonicvernosa
    llama
    common
    ${log-lib}
    ${android-lib}
)
