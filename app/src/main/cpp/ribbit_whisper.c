// SPDX-License-Identifier: MIT
// JNI bridge to whisper.cpp for dev.ribbitos.lite.voice.WhisperNative. Adapted from whisper.cpp's
// examples/whisper.android jni.c (MIT, see NOTICE): load a model file, transcribe 16 kHz mono
// float PCM, free. English only, greedy decoding, nothing printed.
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "whisper.h"

#define MAX_TEXT 16384 // Longest transcript returned; matches Limits.MAX_INPUT_CHARS headroom.

JNIEXPORT jlong JNICALL
Java_dev_ribbitos_lite_voice_WhisperNative_init(JNIEnv *env, jclass cls, jstring model_path) {
    (void) cls;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    if (path == NULL) return 0;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, whisper_context_default_params());
    (*env)->ReleaseStringUTFChars(env, model_path, path);
    return (jlong) ctx;
}

JNIEXPORT jbyteArray JNICALL
Java_dev_ribbitos_lite_voice_WhisperNative_transcribe(JNIEnv *env, jclass cls, jlong handle, jfloatArray pcm,
                                                       jint threads) {
    (void) cls;
    struct whisper_context *ctx = (struct whisper_context *) handle;
    if (ctx == NULL || pcm == NULL) return NULL;
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "en";
    params.n_threads = threads;
    params.no_context = true;
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;

    jsize n = (*env)->GetArrayLength(env, pcm);
    jfloat *samples = (*env)->GetFloatArrayElements(env, pcm, NULL);
    if (samples == NULL) return NULL;
    int failed = whisper_full(ctx, params, samples, n);
    (*env)->ReleaseFloatArrayElements(env, pcm, samples, JNI_ABORT);
    if (failed != 0) return NULL;

    char text[MAX_TEXT];
    size_t used = 0;
    int segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < segments && used < MAX_TEXT - 1; i++) {
        const char *part = whisper_full_get_segment_text(ctx, i);
        size_t len = part == NULL ? 0 : strlen(part);
        if (len > MAX_TEXT - 1 - used) len = MAX_TEXT - 1 - used;
        memcpy(text + used, part, len);
        used += len;
    }
    // Raw UTF-8 bytes, decoded in Kotlin: NewStringUTF only takes "modified" UTF-8 and aborts on
    // characters outside it (emoji, for one).
    jbyteArray out = (*env)->NewByteArray(env, (jsize) used);
    if (out != NULL) (*env)->SetByteArrayRegion(env, out, 0, (jsize) used, (const jbyte *) text);
    return out;
}

JNIEXPORT void JNICALL
Java_dev_ribbitos_lite_voice_WhisperNative_free(JNIEnv *env, jclass cls, jlong handle) {
    (void) env;
    (void) cls;
    if (handle != 0) whisper_free((struct whisper_context *) handle);
}
