cmake_minimum_required(VERSION 3.22.1)
project(mgba_jni CXX C)

# Path to mGBA source (cpp → main → src → emulator-core → project root)
set(MGBA_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../external/mgba")
get_filename_component(MGBA_SOURCE_DIR "${MGBA_SOURCE_DIR}" ABSOLUTE)

# Build mGBA as a static library
add_subdirectory(${MGBA_SOURCE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/mgba)

# Build our JNI shared library
add_library(mgba_jni SHARED
    mgba_jni.cpp
    audio_bridge.cpp
)

target_include_directories(mgba_jni PRIVATE
    ${MGBA_SOURCE_DIR}/include
    ${CMAKE_CURRENT_BINARY_DIR}/mgba/include  # Generated config headers
)

target_link_libraries(mgba_jni
    mgba
    android
    log
    z
)

target_compile_options(mgba_jni PRIVATE -std=c++17)

# Propagate mGBA's compile definitions so headers resolve correctly
get_target_property(MGBA_DEFS mgba COMPILE_DEFINITIONS)
if(MGBA_DEFS)
    target_compile_definitions(mgba_jni PRIVATE ${MGBA_DEFS})
endif()
# Ensure VFS is available (Android is UNIX, uses fd-based VFS)
target_compile_definitions(mgba_jni PRIVATE ENABLE_VFS ENABLE_VFS_FD)
