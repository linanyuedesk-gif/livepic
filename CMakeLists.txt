cmake_minimum_required(VERSION 3.10)
project(vtolive)

# assuming libheif source or compiled library placed under cpp/libheif
# Use the directory of this CMakeLists file rather than CMAKE_SOURCE_DIR, which
# may point to the Android module folder when Gradle invokes CMake. Otherwise
# the relative path becomes incorrect and add_subdirectory fails.
if(EXISTS "${CMAKE_CURRENT_LIST_DIR}/app/src/main/cpp/libheif")
    add_subdirectory("${CMAKE_CURRENT_LIST_DIR}/app/src/main/cpp/libheif")
else()
    message(WARNING "libheif directory not found; HEIF encoding will be disabled.\n"
                    "Run git clone https://github.com/strukturag/libheif.git "
                    "app/src/main/cpp/libheif")
endif()

add_library(native-lib SHARED
            app/src/main/cpp/heif_encoder.cpp)

include_directories(app/src/main/cpp/libheif/include)

target_link_libraries(native-lib PRIVATE heif)