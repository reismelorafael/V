#ifndef RMR_ZERO_COMPAT_H
#define RMR_ZERO_COMPAT_H

#include "zero.h"

#if (RMR_ZERO_ENV_ACTIVE == RMR_ZERO_ENV_BAREMETAL_U8) && defined(__STDC_HOSTED__) && (__STDC_HOSTED__ == 0)
#include "rmr_baremetal_compat.h"
#else
#include "rmr_host_compat.h"
#endif

#include "rmr_corelib.h"

#endif
