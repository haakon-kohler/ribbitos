#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Dumps every R1 partition except userdata through the MediaTek preloader, with mtkclient
# (https://github.com/bkerler/mtkclient; set MTK if `mtk` isn't on PATH). Keeps what rabbit never
# publishes: the current OTA's kernel/vendor, and nvram/nvdata/protect* (IMEI, RF calibration).
# R1 powered off and unplugged; plug it in once mtkclient says it is waiting.
# The dump identifies the device: keep it private, never commit it.
set -euo pipefail
out="${1:?usage: backup.sh <output dir>}"
mtk="${MTK:-mtk}"
mkdir -p "${out}"
"${mtk}" rl "${out}" --skip userdata 2>&1 | tee "${out}/mtkclient.log"
(cd "${out}" && sha256sum -- *.bin >SHA256SUMS)
du -sh "${out}"
