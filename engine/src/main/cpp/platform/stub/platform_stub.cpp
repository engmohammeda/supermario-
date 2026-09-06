/* platform_stub.cpp — no rendering, no audio, for desktop builds and tests.
 *
 * The host must behave identically with and without a platform backend: the
 * emulator thread runs the same loop, the same frame buffer is filled, and the
 * only difference is that nothing is presented and pacing stops (there is
 * nobody to keep time with). That is what makes the Linux host tests a real
 * test of the same code path an Android build runs.
 */
#include "../host/mb_common.h"

#ifndef __ANDROID__

namespace mb {
namespace {

class StubAudio final : public AudioOut {
public:
  bool open(uint32_t sample_rate, uint32_t channels, uint32_t) override {
    rate_ = sample_rate;
    channels_ = channels;
    return true;
  }
  void close() override { }
  size_t write(const int16_t *frames, size_t frame_count) override {
    (void)frames;
    /* A real device blocks when its queue is full; here we consume instantly,
     * which is what a headless run wants. */
    return frame_count;
  }
  void start() override { }
  void stop() override { }
  void set_paused(bool) override { }
  void set_volume(float) override { }
  void drain() override { }
  uint32_t underruns() const override { return 0; }
  const char *backend_name() const override { return "null"; }
  bool is_null() const override { return true; }

private:
  uint32_t rate_ = 0;
  uint32_t channels_ = 0;
};

} /* namespace */

Surface *surface_create() { return nullptr; }
void surface_destroy(Surface *) { }

AudioOut *audio_create() { return new StubAudio(); }
void audio_destroy(AudioOut *a) { delete a; }

} /* namespace mb */

#endif /* !__ANDROID__ */
