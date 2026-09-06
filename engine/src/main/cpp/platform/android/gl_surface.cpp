/* gl_surface.cpp — EGL + GLES2 presenter for Android.
 *
 * Who calls what, and why the code is shaped like this
 * ----------------------------------------------------
 * The host calls attach()/detach()/resize()/apply() from whichever thread the app
 * is on (SurfaceView callbacks), and present() from the emulation thread. EGL state
 * belongs to a thread, so every EGL call in this file happens under `mu_` AND only
 * on the thread that currently owns the context:
 *
 *   • the context is created lazily on the first present(), i.e. on the thread that
 *     will draw, never on the UI thread;
 *   • a detach() arriving from another thread does not destroy anything. It records
 *     the request and returns. The next present() tears the EGL window surface down
 *     and drops our ANativeWindow reference. That is safe because EGL holds its own
 *     reference to the window for as long as the surface exists, so the window
 *     cannot die under us;
 *   • detach() called *on* the GL thread (the common case: mb_set_surface(null)
 *     from a posted job, and every teardown after the emulation thread has joined)
 *     releases immediately.
 *
 * Scaling, overscan, rotation and scanline period are computed in
 * platform/common/render_plan.cpp, which the desktop tests cover. What is left here
 * is one quad, one texture, and a viewport.
 */
#include "gl_surface.h"

#include "render_plan.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/native_window.h>

#include <cmath>
#include <cstring>
#include <pthread.h>
#include <vector>

namespace mb {
namespace {

const char *kVertSrc =
    "attribute vec2 aPos;\n"
    "attribute vec2 aCorner;\n"
    "uniform vec4 uRect;\n"   /* u0,v0,u1,v1 in texture space */
    "uniform int uRot;\n"     /* quarters of clockwise rotation */
    "uniform vec2 uSrc;\n"    /* source size in texels */
    "varying vec2 vUV;\n"
    "varying float vRow;\n"
    "void main() {\n"
    "  float qx = aCorner.x;\n"
    "  float qy = 1.0 - aCorner.y;\n" /* measured from the top of the viewport */
    "  float sx, sy;\n"
    "  if (uRot == 0) { sx = qx; sy = qy; }\n"
    "  else if (uRot == 1) { sx = qy; sy = 1.0 - qx; }\n"
    "  else if (uRot == 2) { sx = 1.0 - qx; sy = 1.0 - qy; }\n"
    "  else { sx = 1.0 - qy; sy = qx; }\n"
    "  vUV = vec2(mix(uRect.x, uRect.z, sx), mix(uRect.y, uRect.w, sy));\n"
    "  vRow = vUV.y * uSrc.y;\n"
    "  gl_Position = vec4(aPos, 0.0, 1.0);\n"
    "}\n";

const char *kFragSrc =
    "precision mediump float;\n"
    "uniform sampler2D uTex;\n"
    "uniform float uScan;\n" /* 0..1, 0 disables the pattern */
    "varying vec2 vUV;\n"
    "varying float vRow;\n"
    "void main() {\n"
    "  vec4 c = texture2D(uTex, vUV);\n"
    "  float t = fract(vRow);\n"
    "  float shade = 1.0 - uScan * (0.5 - 0.5 * cos(6.28318530718 * t));\n"
    "  gl_FragColor = vec4(c.rgb * shade, 1.0);\n"
    "}\n";

/* Clip-space quad plus the 0..1 corner coordinate used for the UV mapping. */
const GLfloat kQuadPos[8] = {-1.f, -1.f, 1.f, -1.f, -1.f, 1.f, 1.f, 1.f};
const GLfloat kQuadCorner[8] = {0.f, 0.f, 1.f, 0.f, 0.f, 1.f, 1.f, 1.f};

class AndroidGLSurface final : public Surface {
public:
  ~AndroidGLSurface() override {
    /* stop() has joined the emulation thread before the host drops us, so by now
     * no one else can be drawing. */
    release_egl_locked(true);
    drop_window_locked();
  }

  bool attach(void *native_window, uint32_t w, uint32_t h) override {
    std::lock_guard<std::mutex> lk(mu_);
    ANativeWindow *win = static_cast<ANativeWindow *>(native_window);
    if (!win) {
      request_release_locked();
      return false;
    }
    if (window_ == win) {
      /* Same window, maybe a new size: resize() handles it. */
      return surf_ != EGL_NO_SURFACE || pending_attach_;
    }
    /* Replacing a live window (rotation, SurfaceView recreated) means the old EGL
     * surface has to go first -- on the thread that owns it. */
    if (window_) {
      if (have_context_here()) {
        release_egl_locked(false);
        drop_window_locked();
      } else {
        /* Hand the old window's reference to the GL thread: it drops it right after
         * it destroys the EGL surface that was still swapping to it. */
        if (dying_) {
          ANativeWindow_release(dying_);
          dying_ = nullptr;
        }
        dying_ = window_;
        window_ = nullptr;
        pending_release_ = true;
        pending_attach_ = false;
      }
    }
    window_ = win; /* we own the reference the JNI layer acquired */
    if (w && h) {
      win_w_ = w;
      win_h_ = h;
    } else {
      win_w_ = uint32_t(ANativeWindow_getWidth(window_));
      win_h_ = uint32_t(ANativeWindow_getHeight(window_));
    }
    pending_attach_ = true;
    /* If we are already on the GL thread (resume-after-rotation, the destructor
     * path re-attaching) just build the surface now instead of losing a frame. */
    if (dpy_ != EGL_NO_DISPLAY && have_context_here())
      pending_attach_ = !build_window_surface_locked();
    MB_LOGI("egl attach window %ux%u", win_w_, win_h_);
    return true;
  }

  void detach() override {
    std::lock_guard<std::mutex> lk(mu_);
    request_release_locked();
  }

  void resize(uint32_t w, uint32_t h) override {
    std::lock_guard<std::mutex> lk(mu_);
    if (!w || !h)
      return;
    if (w == win_w_ && h == win_h_)
      return;
    win_w_ = w;
    win_h_ = h;
    /* Android requires the window surface to be recreated when the buffer geometry
     * changes; doing it here would race the presenting thread, so flag it. */
    need_rebuild_ = (dpy_ != EGL_NO_DISPLAY);
    MB_LOGI("surface resized to %ux%u", w, h);
  }

  void apply(const RenderConfig &cfg) override {
    std::lock_guard<std::mutex> lk(mu_);
    cfg_ = cfg;
    filter_dirty_ = true;
  }

  void geometry_out(uint32_t *w, uint32_t *h) override {
    std::lock_guard<std::mutex> lk(mu_);
    if (w)
      *w = win_w_;
    if (h)
      *h = win_h_;
  }

  void present(const FrameView &fv) override {
    std::lock_guard<std::mutex> lk(mu_);
    if (pending_release_) {
      release_egl_locked(false);
      if (dying_) {
        ANativeWindow_release(dying_);
        dying_ = nullptr;
      }
    }
    if (!window_)
      return;
    if (dpy_ == EGL_NO_DISPLAY && !init_egl_locked())
      return;
    if (need_rebuild_) {
      release_egl_locked(false);
      if (!build_window_surface_locked())
        return;
    }
    if (pending_attach_) {
      if (!build_window_surface_locked())
        return;
    }
    if (surf_ == EGL_NO_SURFACE)
      return;
    if (!make_current_locked())
      return;
    if (prog_ == 0 && !build_program_locked())
      return;
    if (filter_dirty_)
      apply_filter_locked();
    upload_locked(fv);
    draw_locked(fv);

    if (eglSwapBuffers(dpy_, surf_) != EGL_TRUE) {
      EGLint e = eglGetError();
      /* A rotated or backgrounded window makes the surface stale; drop it and let
       * the next present rebuild rather than spam errors. */
      MB_LOGW("eglSwapBuffers failed (EGL error 0x%x); rebuilding window surface", e);
      release_egl_locked(false);
      pending_attach_ = true;
      if (++swap_failures_ > 120) {
        MB_LOGE("swapping failed 120 times in a row; giving up on rendering");
        drop_window_locked();
        swap_failures_ = 0;
      }
    } else {
      swap_failures_ = 0;
    }
  }

private:
  bool have_context_here() const {
    return pthread_equal(gl_thread_, pthread_self()) != 0;
  }

  void request_release_locked() {
    if (dpy_ == EGL_NO_DISPLAY && !pending_attach_) {
      drop_window_locked();
      return;
    }
    if (have_context_here()) {
      release_egl_locked(true);
      drop_window_locked();
      return;
    }
    /* The GL thread will do it. Keep our window reference alive until then: EGL is
     * still swapping to it, and ANativeWindow is refcounted so this is merely a
     * delay in the window's destruction, not a leak. */
    pending_release_ = true;
    pending_attach_ = false;
  }

  void drop_window_locked() {
    if (window_) {
      ANativeWindow_release(window_);
      window_ = nullptr;
    }
    if (dying_) {
      ANativeWindow_release(dying_);
      dying_ = nullptr;
    }
    pending_attach_ = false;
  }

  bool init_egl_locked() {
    dpy_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (dpy_ == EGL_NO_DISPLAY) {
      MB_LOGE("eglGetDisplay failed");
      return false;
    }
    EGLint vmaj = 0, vmin = 0;
    if (eglInitialize(dpy_, &vmaj, &vmin) != EGL_TRUE) {
      MB_LOGE("eglInitialize failed (0x%x)", eglGetError());
      dpy_ = EGL_NO_DISPLAY;
      return false;
    }
    const EGLint ca[] = {EGL_SURFACE_TYPE,    EGL_WINDOW_BIT,
                         EGL_RENDERABLE_TYPE,  EGL_OPENGL_ES2_BIT,
                         EGL_RED_SIZE,        8,
                         EGL_GREEN_SIZE,      8,
                         EGL_BLUE_SIZE,       8,
                         EGL_ALPHA_SIZE,      8,
                         EGL_DEPTH_SIZE,      0,
                         EGL_STENCIL_SIZE,    0,
                         EGL_SAMPLE_BUFFERS,  0,
                         EGL_NONE};
    EGLint n = 0;
    if (eglChooseConfig(dpy_, ca, &cfg_egl_, 1, &n) != EGL_TRUE || n < 1) {
      MB_LOGE("no usable EGL config (0x%x)", eglGetError());
      eglTerminate(dpy_);
      dpy_ = EGL_NO_DISPLAY;
      return false;
    }
    const EGLint ctxa[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    ctx_ = eglCreateContext(dpy_, cfg_egl_, EGL_NO_CONTEXT, ctxa);
    if (ctx_ == EGL_NO_CONTEXT) {
      MB_LOGE("eglCreateContext failed (0x%x)", eglGetError());
      eglTerminate(dpy_);
      dpy_ = EGL_NO_DISPLAY;
      return false;
    }
    MB_LOGI("EGL %d.%d initialised", vmaj, vmin);
    return build_window_surface_locked();
  }

  bool build_window_surface_locked() {
    if (!window_ || dpy_ == EGL_NO_DISPLAY || ctx_ == EGL_NO_CONTEXT)
      return false;
    surf_ = eglCreateWindowSurface(dpy_, cfg_egl_, window_, nullptr);
    if (surf_ == EGL_NO_SURFACE) {
      MB_LOGE("eglCreateWindowSurface failed (0x%x)", eglGetError());
      return false;
    }
    pending_attach_ = false;
    int bw = 0, bh = 0;
    eglQuerySurface(dpy_, surf_, EGL_WIDTH, &bw);
    eglQuerySurface(dpy_, surf_, EGL_HEIGHT, &bh);
    if (bw > 0 && bh > 0) {
      win_w_ = uint32_t(bw);
      win_h_ = uint32_t(bh);
    }
    need_rebuild_ = false;
    return make_current_locked();
  }

  bool make_current_locked() {
    if (current_ && have_context_here())
      return true;
    if (eglMakeCurrent(dpy_, surf_, surf_, ctx_) != EGL_TRUE) {
      MB_LOGE("eglMakeCurrent failed (0x%x)", eglGetError());
      current_ = false;
      return false;
    }
    gl_thread_ = pthread_self();
    current_ = true;
    return true;
  }

  void release_egl_locked(bool destroy_context) {
    if (dpy_ != EGL_NO_DISPLAY) {
      if (current_ && have_context_here()) {
        eglMakeCurrent(dpy_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        current_ = false;
      }
      if (surf_ != EGL_NO_SURFACE) {
        eglDestroySurface(dpy_, surf_);
        surf_ = EGL_NO_SURFACE;
      }
      if (destroy_context) {
        if (ctx_ != EGL_NO_CONTEXT) {
          eglDestroyContext(dpy_, ctx_);
          ctx_ = EGL_NO_CONTEXT;
        }
        eglTerminate(dpy_);
        dpy_ = EGL_NO_DISPLAY;
        cfg_egl_ = nullptr;
        /* A dead program/texture belongs to the context we just destroyed. */
        prog_ = 0;
        tex_ = 0;
        tex_w_ = tex_h_ = 0;
        need_rebuild_ = false;
      }
    }
    pending_release_ = false;
  }

  bool build_program_locked() {
    GLuint vs = compile(GL_VERTEX_SHADER, kVertSrc);
    GLuint fs = compile(GL_FRAGMENT_SHADER, kFragSrc);
    if (!vs || !fs) {
      if (vs)
        glDeleteShader(vs);
      if (fs)
        glDeleteShader(fs);
      return false;
    }
    GLuint p = glCreateProgram();
    glAttachShader(p, vs);
    glAttachShader(p, fs);
    glBindAttribLocation(p, 0, "aPos");
    glBindAttribLocation(p, 1, "aCorner");
    glLinkProgram(p);
    GLint ok = 0;
    glGetProgramiv(p, GL_LINK_STATUS, &ok);
    glDeleteShader(vs);
    glDeleteShader(fs);
    if (!ok) {
      char log[512];
      GLsizei len = 0;
      glGetProgramInfoLog(p, sizeof(log), &len, log);
      MB_LOGE("link failed: %.*s", int(len), log);
      glDeleteProgram(p);
      return false;
    }
    prog_ = p;
    u_rect_ = glGetUniformLocation(p, "uRect");
    u_rot_ = glGetUniformLocation(p, "uRot");
    u_src_ = glGetUniformLocation(p, "uSrc");
    u_scan_ = glGetUniformLocation(p, "uScan");
    u_tex_ = glGetUniformLocation(p, "uTex");
    glUseProgram(p);
    if (u_tex_ >= 0)
      glUniform1i(u_tex_, 0);
    glGenTextures(1, &tex_);
    glBindTexture(GL_TEXTURE_2D, tex_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    filter_dirty_ = true;
    return true;
  }

  void apply_filter_locked() {
    filter_dirty_ = false;
    const GLint f = cfg_.filter_mode == MB_FILTER_LINEAR ? GL_LINEAR : GL_NEAREST;
    glBindTexture(GL_TEXTURE_2D, tex_);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, f);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, f);
  }

  static GLuint compile(GLenum type, const char *src);

  void upload_locked(const FrameView &fv) {
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glBindTexture(GL_TEXTURE_2D, tex_);
    const uint32_t stride = fv.pitch ? fv.pitch : fv.width * 4u;
    const uint8_t *pixels = fv.pixels;
    /* GLES2 has no UNPACK_ROW_LENGTH, so a padded frame buffer is compacted into a
     * staging row-by-row. Usually a no-op because the host packs rows tightly. */
    if (stride != fv.width * 4u) {
      stage_.resize(size_t(fv.width) * 4u * fv.height);
      for (uint32_t y = 0; y < fv.height; y++)
        std::memcpy(stage_.data() + size_t(y) * fv.width * 4u, pixels + size_t(y) * stride,
                    size_t(fv.width) * 4u);
      pixels = stage_.data();
    }
    if (fv.width != tex_w_ || fv.height != tex_h_) {
      glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, GLsizei(fv.width), GLsizei(fv.height), 0, GL_RGBA,
                   GL_UNSIGNED_BYTE, pixels);
      tex_w_ = fv.width;
      tex_h_ = fv.height;
    } else {
      glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, GLsizei(fv.width), GLsizei(fv.height), GL_RGBA,
                      GL_UNSIGNED_BYTE, pixels);
    }
  }

  void draw_locked(const FrameView &fv) {
    PresentPlan plan = make_plan(win_w_, win_h_, fv.width, fv.height, fv.aspect_ratio, cfg_);
    if (!plan.valid)
      return;
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_BLEND);
    glDisable(GL_SCISSOR_TEST);
    glViewport(0, 0, GLsizei(win_w_), GLsizei(win_h_));
    glClearColor(0.f, 0.f, 0.f, 1.f);
    glClear(GL_COLOR_BUFFER_BIT);
    /* The plan reports a top-left origin rectangle; GL wants bottom-left. */
    glViewport(plan.viewport.x, GLsizei(int32_t(win_h_) - plan.viewport.y - plan.viewport.height),
               GLsizei(plan.viewport.width), GLsizei(plan.viewport.height));
    glUseProgram(prog_);
    glUniform4f(u_rect_, plan.u0, plan.v0, plan.u1, plan.v1);
    glUniform1i(u_rot_, plan.rotation_quarters);
    glUniform2f(u_src_, float(fv.width), plan.scanline_rows);
    glUniform1f(u_scan_, plan.scanline_amount);

    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, kQuadPos);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 0, kQuadCorner);
    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);
    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, tex_);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    frames_drawn_++;
  }

  std::mutex mu_;
  ANativeWindow *window_ = nullptr;
  /* A window we are no longer drawing to but the GL thread still has to let go of. */
  ANativeWindow *dying_ = nullptr;
  uint32_t win_w_ = 0, win_h_ = 0;
  RenderConfig cfg_{};
  bool pending_attach_ = false;
  bool pending_release_ = false;
  bool need_rebuild_ = false;
  bool filter_dirty_ = true;
  bool current_ = false;
  int swap_failures_ = 0;
  uint64_t frames_drawn_ = 0;

  EGLDisplay dpy_ = EGL_NO_DISPLAY;
  EGLConfig cfg_egl_ = nullptr;
  EGLContext ctx_ = EGL_NO_CONTEXT;
  EGLSurface surf_ = EGL_NO_SURFACE;
  pthread_t gl_thread_ = 0;

  GLuint prog_ = 0, tex_ = 0;
  GLint u_rect_ = -1, u_rot_ = -1, u_src_ = -1, u_scan_ = -1, u_tex_ = -1;
  uint32_t tex_w_ = 0, tex_h_ = 0;
  std::vector<uint8_t> stage_;
};

GLuint AndroidGLSurface::compile(GLenum type, const char *src) {
  GLuint s = glCreateShader(type);
  glShaderSource(s, 1, &src, nullptr);
  glCompileShader(s);
  GLint ok = 0;
  glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
  if (!ok) {
    char log[512];
    GLsizei len = 0;
    glGetShaderInfoLog(s, sizeof(log), &len, log);
    MB_LOGE("shader compile failed: %.*s", int(len), log);
    glDeleteShader(s);
    return 0;
  }
  return s;
}

} /* namespace */

Surface *create_gl_surface() { return new AndroidGLSurface(); }

} /* namespace mb */
