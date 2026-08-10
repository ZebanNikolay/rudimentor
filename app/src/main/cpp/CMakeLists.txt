cmake_minimum_required(VERSION 3.22.1)
project(rudimentor_audio)

find_package(oboe REQUIRED CONFIG)

add_library(rudimentor_audio SHARED
    MetronomeEngine.cpp
    MicLabEngine.cpp
    OnsetDetector.cpp
    native-lib.cpp
)

target_include_directories(rudimentor_audio PRIVATE ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(rudimentor_audio oboe::oboe android log)
