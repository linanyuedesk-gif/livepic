cmake_minimum_required(VERSION 3.10)

project(vtolive_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LIBHEIF_DIR "${CMAKE_CURRENT_LIST_DIR}/libheif")

if (EXISTS "${LIBHEIF_DIR}/CMakeLists.txt")
    add_subdirectory("${LIBHEIF_DIR}" libheif_build)
elseif (EXISTS "${LIBHEIF_DIR}")
    message(FATAL_ERROR
        "libheif exists but has no CMakeLists.txt.\n"
        "Run: git submodule update --init --recursive")
else()
    message(FATAL_ERROR
        "libheif not found at ${LIBHEIF_DIR}.\n"
        "Run: git submodule update --init --recursive")
endif()

add_library(native-lib SHARED heif_encoder.cpp)
# Use libheif public headers under libheif/api/libheif so we can include <libheif/heif.h>
target_include_directories(native-lib PRIVATE
    "${LIBHEIF_DIR}/libheif/api"
)
target_link_libraries(native-lib PRIVATE heif)
