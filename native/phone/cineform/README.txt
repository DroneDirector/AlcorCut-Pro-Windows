CineForm export
sdk/      CineForm SDK by GoPro (https://github.com/gopro/cineform-sdk, commit 11574d0), Apache 2.0 or MIT — see sdk/LICENSE-APACHE, sdk/LICENSE-MIT. Only the encoder parts are built.
compat/   sse2neon.h (MIT, see compat/LICENSE.sse2neon) maps the SDK's SSE code to ARM NEON; small header stand-ins for Android.
cf_mov.*  .mov writer and pixel conversion (own code).
cineform_jni.cpp  bridge to the app (own code).
