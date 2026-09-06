/* render_plan.h — where the picture goes inside the window, and nothing else.
 *
 * This is the whole presentation policy of the emulator in one pure function:
 * given a window, a framebuffer, the display aspect ratio the core reported and
 * the user's render config, it produces the viewport to draw into, the texture
 * coordinates to sample, and the scanline parameters. No GL, no EGL, no Android
 * -- which is exactly why it lives in platform/common: the desktop host tests
 * assert on it, and every platform renderer is then allowed to be dumb.
 */
#ifndef MARIOBOX_RENDER_PLAN_H
#define MARIOBOX_RENDER_PLAN_H

#include <cstdint>

#include "mb_common.h"

namespace mb {

struct Viewport {
  int32_t x = 0;
  int32_t y = 0;
  int32_t width = 0;
  int32_t height = 0;
};

struct PresentPlan {
  /* Destination rectangle in window pixels, origin bottom-left (GL convention). */
  Viewport viewport;
  /* Texture rect to sample, after overscan crop. u0<v0 is the top-left of the
   * NES frame in memory; the renderer handles the GL y-flip itself. */
  float u0 = 0.f, v0 = 0.f, u1 = 1.f, v1 = 1.f;
  /* Source rows and columns actually visible (for scanline period and debug). */
  float visible_rows = 0.f;
  float visible_cols = 0.f;
  /* 0 = none, 1 = rotate the picture 90 degrees clockwise, 2 = 180, 3 = 270. */
  int rotation_quarters = 0;
  /* 0..1 darkness of the scanline pattern (0 = off). */
  float scanline_amount = 0.f;
  /* Source rows the scanline pattern repeats over (after any crop). */
  float scanline_rows = 0.f;
  /* Window pixels per source row inside `viewport`; the settings screen shows it
   * so a user can tell why a non-integer scale looks soft. */
  float row_height_px = 1.f;
  /* True when the vertical scale is a whole multiple of the source rows. */
  bool scale_is_integer = false;
  /* True when the plan is drawable at all (non-zero viewport and source). */
  bool valid = false;
  /* Overflow when scaling fills past the window (FILL_CROP): the renderer must
   * scissor to the window, these are the pixels lost on each side. */
  int32_t crop_x = 0, crop_y = 0;
};

/* `win_w/win_h` are the window (surface) size in pixels, `src_w/src_h` the
 * framebuffer size, `aspect_ratio` the display aspect ratio the core reported
 * (<= 0 means "square pixels, use src_w/src_h"). */
PresentPlan make_plan(uint32_t win_w, uint32_t win_h, uint32_t src_w, uint32_t src_h,
                      double aspect_ratio, const RenderConfig &cfg);

/* Overscan crop is expressed as a percentage of each axis removed evenly from
 * both edges (0..25). These two helpers are exposed for tests and for the
 * settings UI's preview text. */
void crop_pixels(uint32_t src_w, uint32_t src_h, int overscan_percent, int32_t *out_cx,
                 int32_t *out_cy);

} /* namespace mb */
#endif /* MARIOBOX_RENDER_PLAN_H */
