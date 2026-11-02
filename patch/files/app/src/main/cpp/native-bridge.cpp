#include <jni.h>

// P3a 空壳：只回一个字符串，用来证明 .so 编出来了、也打进 APK 了。
// JNI 函数名必须和 Kotlin 侧的包名 + 类名对上：com.wangzan101.mika.local.NativeBridge.nativeHello
extern "C" JNIEXPORT jstring JNICALL
Java_com_wangzan101_mika_local_NativeBridge_nativeHello(JNIEnv *env, jclass) {
    return env->NewStringUTF("mikalocal-native-ok");
}
