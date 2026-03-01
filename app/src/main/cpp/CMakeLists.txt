cmake_minimum_required(VERSION 3.10)

project(vtolive_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LIBHEIF_DIR "${CMAKE_CURRENT_LIST_DIR}/libheif")

if (EXISTS "${LIBHEIF_DIR}/CMakeLists.txt")
    # Build libheif from the vendored source tree
    add_subdirectory("${LIBHEIF_DIR}" libheif_build)
elseif (EXISTS "${LIBHEIF_DIR}")
    message(FATAL_ERROR
        "libheif directory exists at ${LIBHEIF_DIR} but does not contain a CMakeLists.txt file.\n"
        "Make sure you cloned the full libheif repository, not just an empty folder.\n"
        "Expected command:\n"
        "  git clone https://github.com/strukturag/libheif.git app/src/main/cpp/libheif"
    )
else()
    message(FATAL_ERROR
        "libheif source tree not found at ${LIBHEIF_DIR}.\n"
        "Run one of the following before building:\n"
        "  git submodule update --init --recursive\n"
        "  # or\n"
        "  git clone https://github.com/strukturag/libheif.git app/src/main/cpp/libheif"
    )
endif()

add_library(
    native-lib
    SHARED
    heif_encoder.cpp
)

target_include_directories(native-lib PRIVATE
    "${LIBHEIF_DIR}/include"
)

target_link_libraries(native-lib PRIVATE
    heif
)

