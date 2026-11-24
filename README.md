# Nuvio Face

A Netflix-style face for Nuvio:

- **Nuvio Face** (`com.dave.nuvioface`): phones and tablets, built on [NuvioMobile](https://github.com/NuvioMedia/NuvioMobile).
- **Nuvio Face TV** (`com.dave.nuviofacetv`): Android TV / Google TV, built on Nuvio's official TV app
  [NuvioTV](https://github.com/NuvioMedia/NuvioTV), so it's made for remotes and slow boxes.

Same Nuvio underneath (addons, debrid, sync, Trakt, profiles, watch party, Android Auto); only the look changes.

## How it stays current

Every day at 3:23 AM Pacific, GitHub Actions:

1. finds the latest official NuvioMobile and NuvioTV release tags,
2. applies `patches/nuvio-face.patch` and `patches/nuvio-face-tv.patch` (overlapping additions are kept from both sides),
3. builds the phone APK, the TV APKs (arm64 for Shield, arm32 for Chromecast with Google TV / onn) and the updater,
4. publishes them as a [Release](../../releases/latest).

If nothing changed upstream, it does nothing. If Nuvio changed something the patch can't fit around, the
build fails and GitHub emails you; nothing broken gets published.

Run it by hand: **Actions > Nuvio Face > Run workflow** (optionally pick an official tag, branch or commit).

## Install

Install **NuvioFaceUpdater.apk** from the latest release once on each device. On a TV box it installs Nuvio Face TV,
on a phone Nuvio Face. It shows the installed
and latest version, downloads the right APK for the device, installs it, and can check daily and notify you.

## Signing

Add the secret `NUVIO_FACE_KEYSTORE_B64` (PKCS12, password and alias `nuvioface`) so every build is signed
with the same key and installs over the previous one. Keep a copy of the key somewhere safe: lose it and the
next build can only be installed after uninstalling.

## Changing the face

The phone source is on the `source` branch; the TV source is official NuvioTV at `patches/BASE-TV` plus
`patches/nuvio-face-tv.patch`. After changing it, regenerate the patch against the official base:

    git diff --binary --full-index <official-base-commit> <source-commit> -- . ':(exclude).github' > patches/nuvio-face.patch

`patches/BASE` records the official commit the patch was made from.
