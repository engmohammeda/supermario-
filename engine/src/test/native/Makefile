# Native (desktop) test build for the MarioBox engine host — no Android SDK, no
# device, no emulator. The same C ABI the JNI bridge uses is exercised directly,
# against the real vendored core and the real host code.
#
#   make          build ./test_host and the core .so
#   make run      generate the probe ROM and run the suite
#   make sources  print the source list the core build will use
#
# The core's source list and defines come from the vendored core's own
# Makefile.common (included below), because that file is the only place upstream
# keeps it complete and current — hand-copying it into a build script is how
# mappers go missing at 2 a.m. We only supply the two variables it expects and
# the switches it branches on.

ROOT       := $(abspath ../../../..)
CPP_DIR    := $(ROOT)/engine/src/main/cpp
HOST       := $(CPP_DIR)/host
BRIDGE     := $(CPP_DIR)/bridge
PLATFORM   := $(CPP_DIR)/platform
CORE_DIR   := $(CPP_DIR)/third_party/fceumm_next
BUILD      ?= $(ROOT)/build
OBJDIR     := $(BUILD)/native-obj
BIN        := $(BUILD)/native
CORE_SO    := $(BIN)/libmbcore_fceumm.so
V          ?= 0
ifeq ($(V),1)
  Q :=
else
  Q := @
endif

# ---- inputs Makefile.common expects --------------------------------------
# These four variables are the only inputs Makefile.common branches on, so they
# have to hold *exactly* those values: an inline comment here would become part of
# the value ("1          " != "1") and silently drop the NTSC filter sources and
# -DHAVE_NTSC_FILTER. Comments therefore sit on their own lines.
SOURCE_DIR     := $(CORE_DIR)/src
LIBRETRO_DIR   := $(CORE_DIR)/libretro
# The NTSC composite filter is a shipped feature, not an option.
HAVE_NTSC := 1
# Shared object, so the libretro-common compat sources are compiled in.
STATIC_LINKING := 0
include $(CORE_DIR)/Makefile.common

CC       ?= gcc
CXX      ?= g++
OPT      ?= -O1
# -fno-builtin-sincos: the compiler rewrites a nearby sin()/cos() pair in
# src/nsf.c into a single sincos() call, which glibc and bionic>=28 export but
# older Android does not — and a core .so with an unresolved symbol fails at
# dlopen, on the user's phone, with no diagnostic. Suppressing that one builtin
# keeps the object self-contained on every API level we ship.
CORE_CFLAGS := $(DEPFLAGS) $(OPT) -g -fPIC -std=gnu11 -w -D__LIBRETRO__ $(COREDEFINES) $(INCFLAGS) \
               -I$(CORE_DIR) -include compat/strl.h \
               -fno-builtin-sincos -fno-builtin-sincosf
LIBRETRO_COMM_DIR := $(LIBRETRO_DIR)/libretro-common
HOST_INCLUDES := -I$(CPP_DIR) -I$(HOST) -I$(CPP_DIR)/platform/common -I$(LIBRETRO_COMM_DIR)/include
DEPFLAGS := -MMD -MP
CXXFLAGS := $(DEPFLAGS) -std=gnu++17 $(OPT) -g -Wall -Wextra -Wno-unused-parameter \
            $(HOST_INCLUDES) -pthread
CFLAGS_STUB := $(OPT) -g -Wall $(HOST_INCLUDES)
# Deflate is optional by design (see host/mb_common.h): detect it so a bare
# sandbox still builds, while CI — which has zlib — exercises the compressed
# path that shipped save files actually use.
HAVE_ZLIB := $(shell printf '#include <zlib.h>\nint main(void){return 0;}\n' \
  | $(CC) -x c - -o /dev/null -lz >/dev/null 2>&1 && echo 1)
ifeq ($(HAVE_ZLIB),1)
  HOST_INCLUDES += -DMB_HAVE_ZLIB
  CXXFLAGS      += -DMB_HAVE_ZLIB
  CORE_CFLAGS   += -DMB_HAVE_ZLIB
  LDFLAGS_ZLIB  := -lz
else
  LDFLAGS_ZLIB  :=
endif

LDFLAGS  := -pthread -ldl $(LDFLAGS_ZLIB)

# SOURCES_C from the vendored makefile fragment already resolves to absolute
# paths (SOURCE_DIR/LIBRETRO_DIR are absolute above), so the object name mirrors
# that absolute path under the build dir instead of trying to re-relativise it.
CORE_SRCS  := $(SOURCES_C)
CORE_OBJS  := $(patsubst /%.c,$(OBJDIR)/core/%.o,$(CORE_SRCS))
BRIDGE_OBJ := $(OBJDIR)/bridge/mariobox_fceumm_ext.c.o
HOST_SRCS  := $(wildcard $(HOST)/*.cpp) $(PLATFORM)/stub/platform_stub.cpp $(PLATFORM)/common/render_plan.cpp
HOST_OBJS  := $(patsubst $(ROOT)/%.cpp,$(OBJDIR)/host/%.cpp.o,$(HOST_SRCS))
TEST_BIN   := $(BIN)/test_host
ALL_OBJS   := $(CORE_OBJS) $(BRIDGE_OBJ) $(HOST_OBJS) $(OBJDIR)/test_host.cpp.o
DEPS       := $(ALL_OBJS:.o=.d)

.PHONY: all run clean sources print-build-spec check-source-list
all: $(TEST_BIN) $(CORE_SO)

sources:
	@printf '%s\n' $(SOURCES_C) | wc -l
	@printf '%s\n' $(CORE_SRCS)

# ---- build spec consumed by CMake ----------------------------------------
# The Android build has to compile exactly what this Makefile compiles, so
# rather than duplicating 648 paths into a CMakeLists.txt we print them and let
# tools/gen_cmake_core_list.py commit the answer. The -I/-D juggling here is
# deliberate: Makefile.common puts an include dir in COREDEFINES and the core
# needs the libretro-common compat header force-included, so both lists are
# flattened into the two categories CMake actually cares about.
print-build-spec:
	$(Q)for f in $(SOURCES_C) ; do printf 'src\t%s\n' $$f ; done
	$(Q)printf 'src\t%s\n' $(BRIDGE)/fceumm/mariobox_fceumm_ext.c
	$(Q)for f in $(INCFLAGS) $(COREDEFINES) ; do \
	  case $$f in \
	    -I*) printf 'inc\t%s\n' $${f#-I} ;; \
	    -D*) printf 'def\t%s\n' $${f#-D} ;; \
	    *)   printf 'inc\t%s\n' $$f ;; \
	  esac ; done
	$(Q)printf 'inc\t%s\n' $(CPP_DIR)
	$(Q)printf 'inc\t%s\n' $(HOST)
	$(Q)printf 'force_include\t%s\n' $(LIBRETRO_COMM_DIR)/include/compat/strl.h

check-source-list:
	$(Q)python3 $(ROOT)/tools/gen_cmake_core_list.py --check

$(CORE_SO): $(CORE_OBJS) $(BRIDGE_OBJ)
	@mkdir -p $(dir $@)
	$(CC) -shared -o $@ $^ $(LDFLAGS) $(LDFLAGS_ZLIB) -lm

$(OBJDIR)/core/%.o: /%.c
	@mkdir -p $(dir $@)
	$(CC) $(CORE_CFLAGS) -c $< -o $@

$(BRIDGE_OBJ): $(BRIDGE)/fceumm/mariobox_fceumm_ext.c $(LIBRETRO_COMM_DIR)/include/libretro.h
	@mkdir -p $(dir $@)
	$(CC) $(CORE_CFLAGS) -c $< -o $@

$(TEST_BIN): $(HOST_OBJS) $(OBJDIR)/test_host.cpp.o
	@mkdir -p $(dir $@)
	$(CXX) $^ -o $@ $(LDFLAGS)

$(OBJDIR)/host/%.cpp.o: $(ROOT)/%.cpp
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) -c $< -o $@

-include $(DEPS)

$(OBJDIR)/%.cpp.o: %.cpp
	@mkdir -p $(dir $@)
	$(CXX) $(CXXFLAGS) -c $< -o $@

run: all
	@mkdir -p $(BUILD)/probe $(BUILD)/work
	@python3 $(ROOT)/tools/gen_test_rom.py $(BUILD)/probe/mbprobe.nes > /dev/null
	@rm -f $(BUILD)/work/*.srm $(BUILD)/work/*.mbs
	$(TEST_BIN) --core $(CORE_SO) --rom $(BUILD)/probe/mbprobe.nes --work $(BUILD)/work

clean:
	rm -rf $(OBJDIR) $(BIN)
