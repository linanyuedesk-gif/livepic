#include <jni.h>
#include <string>
#include "heif/heif.h"

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_cl_vtolive_modules_core_LivePhotoEncoder_nativeEncodeHeif(
        JNIEnv* env, jclass /*clazz*/,
        jstring keyPath, jobjectArray motionPaths,
        jstring outPath, jstring xmpStr) {
    const char* key = env->GetStringUTFChars(keyPath, nullptr);
    const char* out = env->GetStringUTFChars(outPath, nullptr);
    const char* xmp = env->GetStringUTFChars(xmpStr, nullptr);

    heif_context* ctx = heif_context_alloc();
    heif_error err = heif_context_read_from_file(ctx, key);
    if (err.code != heif_error_Ok) goto fail;

    int count = env->GetArrayLength(motionPaths);
    for (int i = 0; i < count; i++) {
        jstring pathObj = (jstring) env->GetObjectArrayElement(motionPaths, i);
        const char* path = env->GetStringUTFChars(pathObj, nullptr);
        heif_context* tmp = heif_context_alloc();
        heif_error err2 = heif_context_read_from_file(tmp, path);
        if (err2.code == heif_error_Ok) {
            heif_image_handle* handle;
            err2 = heif_context_get_primary_image_handle(tmp, &handle);
            if (err2.code == heif_error_Ok) {
                heif_context_add_image(ctx, handle);
            }
        }
        env->ReleaseStringUTFChars(pathObj, path);
        env->DeleteLocalRef(pathObj);
        heif_context_free(tmp);
    }

    if (xmp && *xmp) {
        heif_context_set_xmp_metadata(ctx,
                                      reinterpret_cast<const uint8_t*>(xmp),
                                      strlen(xmp));
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

}