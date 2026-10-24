# keys/

`aosp-platform.p12` is **AOSP's public test platform key**, the one in Android's own source tree
(`build/target/product/security/platform.pk8` + `platform.x509.pem`, Apache 2.0), packaged as
PKCS#12 with the usual password `android`. It is not a secret: anyone can download it.

ribbitOS lite signs with it because the LineageOS GSI it runs on is signed with the same key
(certificate SHA-256 `c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8`; check
yours with `apksigner verify --print-certs` on `/system/priv-app/Settings/Settings.apk`). An app
signed with the platform key runs as a "platform app", and rabbit's vendor SELinux policy lets
platform apps turn the R1's camera motor (`sysfs_motor`), and Android lets platform apps install
updates without asking (docs/UPDATES.md).

On an OS signed with its own private keys, this key grants nothing; the camera then can't turn,
updates have to be installed by hand, and ribbitOS says so.

Because this key is public, it can't vouch for an update. That is the job of the **release key**:
its public half is `app/src/main/res/raw/update_key.pem`, its private half stays with whoever
publishes releases and never enters this repository ([docs/UPDATES.md](../docs/UPDATES.md)). Refresh it with:

    base=https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security
    curl -s "$base/platform.x509.pem?format=TEXT" | base64 -d > platform.x509.pem
    curl -s "$base/platform.pk8?format=TEXT" | base64 -d > platform.pk8
    openssl pkcs8 -inform DER -nocrypt -in platform.pk8 -out key.pem
    openssl pkcs12 -export -in platform.x509.pem -inkey key.pem -name platform \
        -password pass:android -out aosp-platform.p12
