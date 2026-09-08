# MarioBox native build.
#
# Two shared objects come out of this file:
#
#   libmariobox_host.so -- the host: libretro loader, frame/cheat/rewind logic,
#                          and the platform backends (EGL/GLES2 + AAudio on
#                          Android, a null backend on the desktop).
#   libmbcore_fceumm.so -- the vendored core plus our bridge, loaded at runtime
#                          with dlopen() from the app's native library directory.
#
# Keeping them separate is what lets a second libretro core be dropped in later
# without symbol collisions, and it is also what the desktop host tests exercise:
# `engine/src/test/native/Makefile` builds the same sources with the same
# defines, so a green local suite means a green core half of the Android build.
#
# The core's source list is NOT written here. It is generated from the vendored
# core's own Makefile.common into fceumm_build_spec.cmake (see
# tools/gen_cmake_core_list.py), because 649 paths copied by hand is where
# mappers go to die. `make -C engine/src/test/native check-source-list` proves
# the generated file still matches the fragment.

cmake_minimum_required(VERSION 3.22)
project(mariobox LANGUAGES C CXX)

# ------------------------------------------------------------------ options
option(MB_BUILD_CORES "Build the bundled libretro cores as loadable .so files" ON)
option(MB_CORE_FCEUMM "Build libmbcore_fceumm.so (FCEUmm Next)" ON)
# Not shippable yet: the vendored nescc tree does not build as a libretro core
# without surgery upstream (see docs/THIRD_PARTY.md). The target below stays
# disabled so the option, the CMake plumbing and the loader path are all real,
# and enabling it later is a one-line change.
option(MB_CORE_NESCC "Build libmbcore_nescc.so (experimental, needs upstream fixes)" OFF)
option(MB_SANITIZE "Build host + tests with ASan/UBSan" OFF)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
endif()

# The NDK/MSVC defaults are fine for the core (it is 650 C files of dense CPU
# emulation); -O2 is what the desktop tests are green with.
set(MB_CORE_OPT "-O2" CACHE STRING "optimisation flags for the emulation cores")

# ------------------------------------------------------------------ helpers
find_package(ZLIB QUIET)
if(ZLIB_FOUND)
  set(MB_HAVE_ZLIB ON)
else()
  # The NDK ships zlib but does not always resolve find_package(ZLIB) for every
  # API level, so fall back to a raw -lz probe: zlib is an optimisation for
  # save states, not a hard dependency (see host/mb_common.h).
  include(CheckIncludeFile)
  include(CheckCSourceCompiles)
  check_include_file(zlib.h MB_HAVE_ZLIB_HEADER)
  if(MB_HAVE_ZLIB_HEADER)
    set(CMAKE_REQUIRED_LIBRARIES z)
    check_c_source_compiles("#include <zlib.h>\nint main(void){return (int)compressBound(1);}"
                            MB_ZLIB_LINKS)
    set(CMAKE_REQUIRED_LIBRARIES)
    set(MB_HAVE_ZLIB ${MB_ZLIB_LINKS})
  endif()
endif()

set(MB_HOST ${CMAKE_CURRENT_SOURCE_DIR}/host)
set(MB_CPP ${CMAKE_CURRENT_SOURCE_DIR})
set(MB_CORE_COMMON
  ${CMAKE_CURRENT_SOURCE_DIR}/third_party/fceumm_next/libretro/libretro-common/include)

add_compile_options(
  $<$<COMPILE_LANGUAGE:CXX>:-Wall>
  $<$<COMPILE_LANGUAGE:CXX>:-Wextra>
  $<$<COMPILE_LANGUAGE:CXX>:-Wno-unused-parameter>)

if(MB_SANITIZE)
  add_compile_options(-fsanitize=address,undefined -fno-omit-frame-pointer -g)
  add_link_options(-fsanitize=address,undefined)
endif()

# ------------------------------------------------------------------ host
file(GLOB MB_HOST_SOURCES ${MB_HOST}/*.cpp)

if(ANDROID)
  set(MB_PLATFORM_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/platform/android/platform_android.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/platform/android/gl_surface.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/platform/android/aaudio_out.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/platform/android/mariobox_jni.cpp
    # render_plan.cpp is platform-free on purpose: it is the scaling/rotation
    # maths, and it is unit-tested on the desktop where no GPU is involved.
    ${CMAKE_CURRENT_SOURCE_DIR}/platform/common/render_plan.cpp)
else()
  set(MB_PLATFORM_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/platform/stub/platform_stub.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/platform/common/render_plan.cpp)
endif()

add_library(mariobox_host SHARED
  ${MB_HOST_SOURCES}
  ${MB_PLATFORM_SOURCES})

target_include_directories(mariobox_host PRIVATE
  ${MB_CPP}
  ${MB_HOST}
  ${MB_CORE_COMMON}
  ${CMAKE_CURRENT_SOURCE_DIR}/platform/common)

target_compile_features(mariobox_host PRIVATE cxx_std_17)

if(MB_HAVE_ZLIB)
  target_compile_definitions(mariobox_host PRIVATE MB_HAVE_ZLIB)
endif()

find_package(Threads REQUIRED)
target_link_libraries(mariobox_host PRIVATE Threads::Threads ${CMAKE_DL_LIBS})
if(ZLIB_FOUND)
  target_link_libraries(mariobox_host PRIVATE ZLIB::ZLIB)
elseif(MB_HAVE_ZLIB)
  target_link_libraries(mariobox_host PRIVATE z)
endif()

if(ANDROID)
  target_compile_definitions(mariobox_host PRIVATE MB_PLATFORM_ANDROID=1 PATH_MAX=1024)
  target_link_libraries(mariobox_host PRIVATE
    log        # __android_log_print, used by MB_LOG*
    android    # ANativeWindow_*
    EGL
    GLESv2
    aaudio)    # AAudio is API 26+, which is our minSdk (ADR-0002)
endif()

# ------------------------------------------------------------------ cores
# The core needs the exact define/include set the desktop build uses, including
# a force-included compat header (strlcpy/strlcat) and the two -fno-builtin
# switches that keep the .so free of a sincos() dependency older bionic lacks.
function(mariobox_core_common target)
  target_include_directories(${target} PRIVATE ${ARGN})
  target_compile_options(${target} PRIVATE
    ${MB_CORE_OPT} -g -std=gnu11 -w -fPIC
    -fno-builtin-sincos -fno-builtin-sincosf)
  if(ANDROID)
    # bionic has no PATH_MAX in the places fceu's own headers expect it, and the
    # compat strl.h the core relies on is force-included by the desktop build.
    target_compile_definitions(${target} PRIVATE PATH_MAX=1024)
  endif()
  # No version script: mbx_* bridge symbols must stay exported, they are what
  # the host's optional per-core extension table looks up after dlopen().
  set_target_properties(${target} PROPERTIES C_VISIBILITY_PRESET default)
endfunction()

if(MB_BUILD_CORES AND MB_CORE_FCEUMM)
  include(${CMAKE_CURRENT_SOURCE_DIR}/fceumm_build_spec.cmake)

  set(MB_FCEUMM_ABS "")
  foreach(src IN LISTS MBCORE_FCEUMM_SOURCES)
    list(APPEND MB_FCEUMM_ABS ${CMAKE_CURRENT_SOURCE_DIR}/${src})
  endforeach()
  set(MB_FCEUMM_INC "")
  foreach(inc IN LISTS MBCORE_FCEUMM_INCLUDES)
    list(APPEND MB_FCEUMM_INC ${CMAKE_CURRENT_SOURCE_DIR}/${inc})
  endforeach()
  set(MB_FCEUMM_FORCE "")
  foreach(fi IN LISTS MBCORE_FCEUMM_FORCE_INCLUDES)
    list(APPEND MB_FCEUMM_FORCE -include ${CMAKE_CURRENT_SOURCE_DIR}/${fi})
  endforeach()

  add_library(mbcore_fceumm SHARED ${MB_FCEUMM_ABS})
  mariobox_core_common(mbcore_fceumm ${MB_FCEUMM_INC})
  target_compile_definitions(mbcore_fceumm PRIVATE __LIBRETRO__ FRONTEND_SUPPORTS_ARGB888 ${MBCORE_FCEUMM_DEFINES})
  target_compile_options(mbcore_fceumm PRIVATE ${MB_FCEUMM_FORCE})
  if(MB_HAVE_ZLIB)
    target_compile_definitions(mbcore_fceumm PRIVATE MB_HAVE_ZLIB)
  endif()
  target_link_libraries(mbcore_fceumm PRIVATE m)
  if(ZLIB_FOUND)
    target_link_libraries(mbcore_fceumm PRIVATE ZLIB::ZLIB)
  elseif(MB_HAVE_ZLIB)
    target_link_libraries(mbcore_fceumm PRIVATE z)
  endif()
  if(ANDROID)
    target_link_libraries(mbcore_fceumm PRIVATE log)
  endif()
  set_target_properties(mbcore_fceumm PROPERTIES
    LIBRARY_OUTPUT_NAME mbcore_fceumm
    OUTPUT_NAME mbcore_fceumm)

  if(NOT CMAKE_CROSSCOMPILING)
    # Lets the desktop host test point --core at whatever this build produced.
    add_custom_command(TARGET mbcore_fceumm POST_BUILD
      COMMAND ${CMAKE_COMMAND} -E echo "core: $<TARGET_FILE:mbcore_fceumm>")
  endif()
endif()

if(MB_BUILD_CORES AND MB_CORE_NESCC)
  # Kept honest: refuse loudly instead of producing a half-built core.
  message(FATAL_ERROR
    "MB_CORE_NESCC is not buildable from the vendored tree yet -- see "
    "docs/THIRD_PARTY.md (nescc section) for what upstream needs first.")
endif()

# ------------------------------------------------------------------ summary
get_target_property(MB_HOST_SRC mariobox_host SOURCES)
list(LENGTH MB_HOST_SRC MB_HOST_N)
message(STATUS "mariobox_host: ${MB_HOST_N} translation units, platform=${ANDROID}")
message(STATUS "zlib for save-state compression: ${MB_HAVE_ZLIB}")
