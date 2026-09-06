/* gl_surface.h — the Android EGL/GLES2 surface for the host. */
#ifndef MARIOBOX_GL_SURFACE_H
#define MARIOBOX_GL_SURFACE_H

#include "mb_common.h"

namespace mb {

/* The host owns exactly one Surface per session and drives it from the emulation
 * thread, which is also the thread that ends up owning the EGL context. */
Surface *create_gl_surface();

} /* namespace mb */
#endif /* MARIOBOX_GL_SURFACE_H */
