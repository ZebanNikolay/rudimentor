if(NOT TARGET oboe::oboe)
add_library(oboe::oboe SHARED IMPORTED)
set_target_properties(oboe::oboe PROPERTIES
    IMPORTED_LOCATION "/home/user/.gradle/caches/8.11.1/transforms/215a562de297028f67832f6890af2dd9/transformed/oboe-1.10.0/prefab/modules/oboe/libs/android.arm64-v8a/liboboe.so"
    INTERFACE_INCLUDE_DIRECTORIES "/home/user/.gradle/caches/8.11.1/transforms/215a562de297028f67832f6890af2dd9/transformed/oboe-1.10.0/prefab/modules/oboe/include"
    INTERFACE_LINK_LIBRARIES ""
)
endif()

