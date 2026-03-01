cmake_minimum_required(VERSION 3.10)
project(vtolive)

# assuming libheif source or compiled library placed under cpp/libheif
if(EXISTS "${CMAKE_SOURCE_DIR}/app/src/main/cpp/libheif")
    add_subdirectory(app/src/main/cpp/libheif)
else()
    message(WARNING "libheif directory not found; HEIF encoding will be disabled.\n"
                    "Run git clone https://github.com/strukturag/libheif.git "
                    "app/src/main/cpp/libheif")
endif()

add_library(native-lib SHARED
            app/src/main/cpp/heif_encoder.cpp)

include_directories(app/src/main/cpp/libheif/include)

target_link_libraries(native-lib PRIVATE heif)