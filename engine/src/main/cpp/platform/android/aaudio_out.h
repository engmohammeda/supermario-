/* aaudio_out.h — the Android AAudio backend for the host. */
#ifndef MARIOBOX_AAUDIO_OUT_H
#define MARIOBOX_AAUDIO_OUT_H

#include "mb_common.h"

namespace mb {

/* AAudio is API 26+, which is our minSdk (docs/PLAN.md ADR-0002), so the library is
 * linked normally instead of dlopen'd: no half-working fallback path to maintain. */
AudioOut *create_aaudio_out();

} /* namespace mb */
#endif /* MARIOBOX_AAUDIO_OUT_H */
