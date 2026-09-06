/* aaudio_out.cpp — AAudio output for the emulation thread.
 *
 * The host's pacing contract is that write() blocks when the device queue is full,
 * because that is what turns the audio clock into the emulator's clock. AAudio's
 * blocking write gives us that, with two deliberate hardening choices:
 *
 *   • The wait is always bounded (200 ms). AAudio documents "0 = wait indefinitely"
 *     for some build combinations and "0 = return immediately" for others, which is
 *     exactly the kind of ambiguity that hangs an emulator on a user's phone, so we
 *     pass a positive timeout and treat a short write as a dropped batch plus one
 *     underrun. Losing 5 ms of audio is recoverable; a stuck emu thread is not.
 *   • Every error path returns "frames consumed". The host must never be able to
 *     stall on a dead audio device (headset yank, focus loss, stream closed by the
 *     error callback), and muting is the right outcome there.
 *
 * Volume is a software gain applied here rather than AAudioStream_setVolume(), which
 * is API 31; a float multiply on 735 samples a frame is free and works everywhere.
 */
#include "aaudio_out.h"

#include <aaudio/AAudio.h>

#include <algorithm>
#include <cmath>
#include <vector>

namespace mb {
namespace {

constexpr int64_t kWriteTimeoutNs = 200 * 1000 * 1000; /* 200 ms */

class AAudioOut final : public AudioOut {
public:
  bool open(uint32_t sample_rate, uint32_t channels, uint32_t latency_frames) override {
    AAudioStreamBuilder *builder = nullptr;
    aaudio_result_t rc = AAudio_createStreamBuilder(&builder);
    if (rc != AAUDIO_OK || !builder) {
      MB_LOGE("AAudio_createStreamBuilder failed: %s", AAudio_convertResultToText(rc));
      return false;
    }
    rate_ = sample_rate ? int32_t(sample_rate) : 44100;
    channels_ = channels ? int32_t(channels) : 2;
    req_rate_ = rate_;
    req_channels_ = channels_;
    AAudioStreamBuilder_setSampleRate(builder, rate_);
    AAudioStreamBuilder_setChannelCount(builder, channels_);
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    /* EXCLUSIVE would shave another burst of latency but takes the device and
     * survives a focus change less gracefully; SHARED is what a game emulator
     * should hold while a notification plays over it. */
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    const int32_t capacity = std::max<int32_t>(rate_ / 10, int32_t(latency_frames) * 4);
    AAudioStreamBuilder_setBufferCapacityInFrames(builder, capacity);
    AAudioStreamBuilder_setErrorCallback(builder, &AAudioOut::on_error, this);

    rc = AAudioStreamBuilder_openStream(builder, &stream_);
    AAudioStreamBuilder_delete(builder);
    if (rc != AAUDIO_OK || !stream_) {
      MB_LOGE("AAudio openStream failed: %s", AAudio_convertResultToText(rc));
      stream_ = nullptr;
      return false;
    }
    /* The device may quietly disagree with the requested rate. Log it and keep
     * writing at the core's rate rather than resampling: the emulator's timing
     * comes from the core geometry, so a rate mismatch shows up as a slightly
     * different playback speed, which is what every NES frontend does anyway. */
    const int32_t actual_rate = AAudioStream_getSampleRate(stream_);
    if (actual_rate != rate_)
      MB_LOGW("AAudio stream runs at %d Hz, core asked for %d Hz", actual_rate, rate_);
    rate_ = AAudioStream_getSampleRate(stream_);
    channels_ = AAudioStream_getChannelCount(stream_);
    frames_per_capacity_ = AAudioStream_getBufferCapacityInFrames(stream_);
    MB_LOGI("AAudio open: %d Hz x %d ch, capacity %d frames, burst %d", rate_, channels_,
            frames_per_capacity_, AAudioStream_getFramesPerBurst(stream_));
    scratch_.reserve(size_t(std::max(1, frames_per_capacity_)) * size_t(channels_));
    return true;
  }

  void close() override {
    if (!stream_)
      return;
    AAudioStream_requestStop(stream_);
    AAudioStream_close(stream_);
    stream_ = nullptr;
    MB_LOGI("AAudio closed after %llu frames written",
            static_cast<unsigned long long>(written_));
  }

  size_t write(const int16_t *frames, size_t frame_count) override {
    if (!stream_ || !frame_count)
      return frame_count;
    const int16_t *src = frames;
    if (volume_ < 0.999f) {
      if (volume_ <= 0.001f)
        return frame_count; /* muted: consume and drop */
      scratch_.resize(frame_count * size_t(channels_));
      for (size_t i = 0; i < frame_count * size_t(channels_); i++)
        scratch_[i] = int16_t((int32_t(frames[i]) * int32_t(volume_ * 32768.f)) >> 15);
      src = scratch_.data();
    }
    if (needs_reopen_) {
      /* The device changed under us. Reopen on this thread (the one that writes),
       * which is what AAudio asks for after a DISCONNECTED callback. */
      needs_reopen_ = false;
      close();
      if (!open(uint32_t(req_rate_), uint32_t(req_channels_), 0) || !stream_)
        return frame_count;
    }
    aaudio_result_t rc =
        AAudioStream_write(stream_, src, int32_t(frame_count), kWriteTimeoutNs);
    if (rc < 0) {
      /* Device went away mid-write (focus loss, headset, server restart). */
      errors_++;
      if (errors_ == 1 || (errors_ % 240) == 0)
        MB_LOGW("AAudio write failed: %s", AAudio_convertResultToText(rc));
      return frame_count;
    }
    written_ += uint64_t(rc);
    if (size_t(rc) < frame_count) {
      dropped_ += uint32_t(frame_count - size_t(rc));
      underruns_++;
    }
    return size_t(rc);
  }

  void start() override {
    if (stream_) {
      aaudio_result_t rc = AAudioStream_requestStart(stream_);
      if (rc != AAUDIO_OK)
        MB_LOGW("AAudio requestStart: %s", AAudio_convertResultToText(rc));
    }
  }

  void stop() override {
    if (stream_) {
      AAudioStream_requestStop(stream_);
      /* Stop, not flush: the queued tail is short and cutting it is what a user
       * hears as "paused now" once the pause path has done its own fade. */
    }
  }

  void set_paused(bool paused) override {
    if (!stream_)
      return;
    if (paused) {
      if (!paused_) {
        AAudioStream_requestStop(stream_);
        paused_ = true;
      }
    } else if (paused_) {
      AAudioStream_requestStart(stream_);
      paused_ = false;
    }
  }

  void set_volume(float v) override {
    if (v < 0.f)
      v = 0.f;
    if (v > 1.f)
      v = 1.f;
    volume_ = v;
  }

  void drain() override {
    /* Nothing to do: AAudio drains on its own clock and we never hold a backlog,
     * because a full queue is what paces the emulator in the first place. */
  }

  uint32_t underruns() const override {
    uint32_t xruns = 0;
    if (stream_)
      xruns = uint32_t(std::max(0, AAudioStream_getXRunCount(stream_)));
    return xruns + underruns_;
  }

  const char *backend_name() const override { return "AAudio"; }

  ~AAudioOut() override { close(); }

private:
  static int on_error(AAudioStream *stream, void *user, aaudio_result_t error) {
    (void)stream;
    AAudioOut *self = static_cast<AAudioOut *>(user);
    if (!self)
      return 0;
    self->errors_++;
    MB_LOGW("AAudio error callback: %s", AAudio_convertResultToText(error));
    self->needs_reopen_ = true; /* handled by the next write() on the emu thread */
    return 0;
  }

  AAudioStream *stream_ = nullptr;
  int32_t rate_ = 44100;
  int32_t channels_ = 2;
  int32_t req_rate_ = 44100;  /* what the caller asked for, kept for the reopen path */
  int32_t req_channels_ = 2;
  int32_t frames_per_capacity_ = 0;
  float volume_ = 1.f;
  bool paused_ = false;
  bool needs_reopen_ = false;
  uint64_t written_ = 0;
  uint64_t dropped_ = 0;
  uint32_t underruns_ = 0;
  uint32_t errors_ = 0;
  std::vector<int16_t> scratch_;
};

} /* namespace */

AudioOut *create_aaudio_out() { return new AAudioOut(); }

} /* namespace mb */
