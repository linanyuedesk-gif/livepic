#include <jni.h>
#include <string>
#include <cstring>
#include "heif/heif.h"

extern "C" {

/*
 * Apple Live Photo format requires TWO separate files:
 * 1. HEIC: key (still) image only, with ContentIdentifier in metadata
 * 2. MOV: HEVC video, with com.apple.quicktime.content.identifier matching
 *
 * This encodes only the key frame to HEIC. Motion is in a separate MOV file.
 */
JNIEXPORT jboolean JNICALL
Java_com_cl_vtolive_modules_core_LivePhotoEncoder_nativeEncodeHeicKeyOnly(
        JNIEnv* env, jclass /*clazz*/,
        jstring keyPath, jstring outPath, jstring xmpStr) {
    const char* key = env->GetStringUTFChars(keyPath, nullptr);
    const char* out = env->GetStringUTFChars(outPath, nullptr);
    const char* xmp = env->GetStringUTFChars(xmpStr, nullptr);

    heif_context* ctx = heif_context_alloc();
    heif_error err = heif_context_read_from_file(ctx, key);
    if (err.code != heif_error_Ok) goto fail;

    if (xmp && *xmp) {
        heif_context_set_xmp_metadata(ctx,
                                      reinterpret_cast<const uint8_t*>(xmp),
                                      static_cast<size_t>(strlen(xmp)));
    }

    err = heif_context_write_to_file(ctx, out);
    heif_context_free(ctx);

    env->ReleaseStringUTFChars(keyPath, key);
    env->ReleaseStringUTFChars(outPath, out);
    env->ReleaseStringUTFChars(xmpStr, xmp);

    return err.code == heif_error_Ok;

fail:
    heif_context_free(ctx);
    env->ReleaseStringUTFChars(keyPath, key);
    env->ReleaseStringUTFChars(outPath, out);
    env->ReleaseStringUTFChars(xmpStr, xmp);
    return JNI_FALSE;
}

}  // extern "C"