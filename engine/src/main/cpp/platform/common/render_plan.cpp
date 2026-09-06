/* render_plan.cpp — see render_plan.h for the contract.
 *
 * Conventions that keep this testable and the renderer dumb:
 *   • Viewport rectangles are in window pixels with a TOP-LEFT origin (like
 *     Android/Compose), because that is the space the settings UI reasons about.
 *     The GL renderer converts to its bottom-left origin in one line.
 *   • Rotation is baked into the rectangle: the plan says where the already
 *     rotated picture sits, so `present()` never has to think about it.
 *   • Overscan crop and FILL_CROP shrink the *sampled texture rect*, never the
 *     framebuffer, so cropping can never invalidate a texture upload.
 */
#include "render_plan.h"

#include <algorithm>
#include <cmath>

namespace mb {

void crop_pixels(uint32_t src_w, uint32_t src_h, int overscan_percent, int32_t *out_cx,
                 int32_t *out_cy) {
  const int32_t w = int32_t(src_w ? src_w : 1);
  const int32_t h = int32_t(src_h ? src_h : 1);
  int pct = overscan_percent;
  if (pct < 0)
    pct = 0;
  if (pct > 25)
    pct = 25; /* 25% of each axis is half the picture gone; that is enough */
  int32_t cx = int32_t(std::lround(double(w) * double(pct) / 100.0)) / 2;
  int32_t cy = int32_t(std::lround(double(h) * double(pct) / 100.0)) / 2;
  /* Never crop the picture out of existence: leave at least one row and column. */
  cx = std::min(cx, w - 1);
  cy = std::min(cy, h - 1);
  if (out_cx)
    *out_cx = std::max(cx, 0);
  if (out_cy)
    *out_cy = std::max(cy, 0);
}

/* Rectangle of `rw x rh` at `rx,ry` inside a `bw x bh` box, centred. */
static void centre(double bw, double bh, double rw, double rh, double *rx, double *ry) {
  *rx = (bw - rw) * 0.5;
  *ry = (bh - rh) * 0.5;
  if (*rx < 0)
    *rx = 0;
  if (*ry < 0)
    *ry = 0;
}

PresentPlan make_plan(uint32_t win_w, uint32_t win_h, uint32_t src_w, uint32_t src_h,
                      double aspect_ratio, const RenderConfig &cfg) {
  PresentPlan p;
  if (!win_w || !win_h || !src_w || !src_h)
    return p;

  int32_t cx = 0, cy = 0;
  crop_pixels(src_w, src_h, cfg.overscan_crop, &cx, &cy);

  const double vcols = double(src_w) - 2.0 * double(cx);
  const double vrows = double(src_h) - 2.0 * double(cy);
  p.visible_cols = float(vcols);
  p.visible_rows = float(vrows);

  const double u_edge = double(cx) / double(src_w);
  const double v_edge = double(cy) / double(src_h);
  p.u0 = float(u_edge);
  p.u1 = float(1.0 - u_edge);
  p.v0 = float(v_edge);
  p.v1 = float(1.0 - v_edge);

  /* aspect_ratio is the display aspect of the whole framebuffer, so the pixel
   * aspect falls out of it and carries through the crop unchanged. */
  const double fb_aspect = double(src_w) / double(src_h);
  const double par = (aspect_ratio > 0.01) ? (aspect_ratio / fb_aspect) : 1.0;

  int quarters = ((cfg.rotation % 360) + 360) % 360 / 90;
  if (quarters < 0 || quarters > 3)
    quarters = 0;
  p.rotation_quarters = quarters;
  const bool swap = (quarters == 1 || quarters == 3);

  /* Lay the picture out in the box it will be seen in: with rotation that is the
   * window with its sides exchanged, and the transform at the end of this
   * function moves the resulting rectangle back into window coordinates. */
  const double box_w = swap ? double(win_h) : double(win_w);
  const double box_h = swap ? double(win_w) : double(win_h);

  /* Width and height of one source row, in display units. */
  const double unit_w = par;
  double scale = 1.0;
  switch (cfg.scale_mode) {
  case MB_SCALE_STRETCH:
    break; /* fills the box and ignores aspect: handled below */
  case MB_SCALE_FILL_CROP:
    scale = std::max(box_w / (vcols * unit_w), box_h / vrows);
    break;
  case MB_SCALE_INTEGER:
    scale = std::floor(std::max(1.0, std::min(box_w / (vcols * unit_w), box_h / vrows)));
    break;
  case MB_SCALE_FIT:
  default:
    scale = std::min(box_w / (vcols * unit_w), box_h / vrows);
    break;
  }
  if (!(scale > 0.0))
    scale = 1.0;

  double rw, rh, rx = 0, ry = 0;
  if (cfg.scale_mode == MB_SCALE_STRETCH) {
    rw = box_w;
    rh = box_h;
    centre(box_w, box_h, rw, rh, &rx, &ry);
  } else {
    rw = vcols * unit_w * scale;
    rh = vrows * scale;
    if (cfg.scale_mode == MB_SCALE_FILL_CROP && (rw > box_w || rh > box_h)) {
      /* Cover: trim the sampled rect so the overflow is cropped off the picture
       * rather than squashing it or leaving bars. */
      if (rw > box_w + 0.5) {
        const double drop_cols = (vcols - vcols * (box_w / rw)) * 0.5;
        const double du = drop_cols / double(src_w);
        p.u0 = float(u_edge + du);
        p.u1 = float(1.0 - u_edge - du);
        p.visible_cols = float(vcols - 2.0 * drop_cols);
        p.crop_x = int32_t(std::lround((rw - box_w) * 0.5));
        rw = box_w;
      }
      if (rh > box_h + 0.5) {
        const double drop_rows = (vrows - vrows * (box_h / rh)) * 0.5;
        const double dv = drop_rows / double(src_h);
        p.v0 = float(v_edge + dv);
        p.v1 = float(1.0 - v_edge - dv);
        p.visible_rows = float(vrows - 2.0 * drop_rows);
        p.crop_y = int32_t(std::lround((rh - box_h) * 0.5));
        rh = box_h;
      }
    } else {
      /* FIT and FILL both clamp rather than crop: an integer scale can exceed the
       * box only when the window is smaller than a source pixel row. */
      if (rw > box_w)
        rw = box_w;
      if (rh > box_h)
        rh = box_h;
      centre(box_w, box_h, rw, rh, &rx, &ry);
    }
  }

  p.scanline_amount = float(std::max(0, std::min(100, cfg.scanlines_percent))) / 100.0f;
  /* Keep the scanline period honest after a FILL_CROP trim. */
  p.scanline_rows = p.visible_rows > 0.f ? p.visible_rows : float(src_h);

  int32_t vx = int32_t(std::lround(rx));
  int32_t vy = int32_t(std::lround(ry));
  int32_t vw = int32_t(std::lround(rw));
  int32_t vh = int32_t(std::lround(rh));
  if (quarters == 1) { /* 90 clockwise: (u,v) -> (box_h - v, u) */
    vx = int32_t(std::lround(box_h - (ry + rh)));
    vy = int32_t(std::lround(rx));
    std::swap(vw, vh);
  } else if (quarters == 2) { /* 180 */
    vx = int32_t(std::lround(box_w - (rx + rw)));
    vy = int32_t(std::lround(box_h - (ry + rh)));
  } else if (quarters == 3) { /* 270 clockwise: (u,v) -> (v, box_w - u) */
    vx = int32_t(std::lround(ry));
    vy = int32_t(std::lround(box_w - (rx + rw)));
    std::swap(vw, vh);
  }
  /* The rectangle lives in real window space even though it was composed in the
   * exchanged box, so the clamps are always win_w / win_h. */
  const int32_t lim_w = int32_t(win_w);
  const int32_t lim_h = int32_t(win_h);
  p.viewport.x = std::max(0, std::min(vx, lim_w));
  p.viewport.y = std::max(0, std::min(vy, lim_h));
  p.viewport.width = std::max(0, std::min(vw, lim_w - p.viewport.x));
  p.viewport.height = std::max(0, std::min(vh, lim_h - p.viewport.y));

  const double row_axis =
      swap ? double(p.viewport.width) : double(p.viewport.height);
  p.row_height_px = p.scanline_rows > 0.f ? float(row_axis / double(p.scanline_rows)) : 1.f;
  const double sc = p.scanline_rows > 0.f ? row_axis / double(p.scanline_rows) : 0.0;
  p.scale_is_integer = cfg.scale_mode != MB_SCALE_STRETCH &&
                       (cfg.scale_mode == MB_SCALE_INTEGER ||
                        (sc >= 1.0 && std::fabs(sc - std::round(sc)) < 0.01));

  p.valid = p.viewport.width > 0 && p.viewport.height > 0;
  return p;
}

} /* namespace mb */
