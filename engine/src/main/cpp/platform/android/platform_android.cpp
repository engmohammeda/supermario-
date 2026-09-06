/* platform_android.cpp — the Android back-end of the host's two platform seams.
 *
 * host/mb_common.h declares surface_create()/audio_create() and each platform
 * answers them. The desktop build answers with a null backend (platform/stub);
 * this file answers with EGL/GLES2 and AAudio. Nothing here knows about the
 * emulator, and nothing in host/ knows about Android -- that boundary is what
 * keeps the Linux host tests a real test of the shipped code path.
 */
#include "mb_common.h"

#include "aaudio_out.h"
#include "gl_surface.h"

#ifdef __ANDROID__

namespace mb {

Surface *surface_create() { return create_gl_surface(); }
void surface_destroy(Surface *s) { delete s; }

AudioOut *audio_create() { return create_aaudio_out(); }
void audio_destroy(AudioOut *a) { delete a; }

} /* namespace mb */

#endif /* __ANDROID__ */
