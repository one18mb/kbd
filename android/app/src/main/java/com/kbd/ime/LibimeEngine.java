package com.kbd.ime;

/**
 * libime 的 JNI 封装。M2.0 已打通 load / input / candidates / select。
 */
public final class LibimeEngine implements EngineBridge {

    static {
        System.loadLibrary("kbd_engine");
    }

    private static native String nativeVersion();

    private static native String nativeSelfTest();

    private static native void nativeLoad(String dir);

    private static native boolean nativeIsReady();

    private static native void nativeSetShuangpin(boolean flypy);

    private static native boolean nativeInput(char key);

    private static native void nativeBackspace();

    private static native State nativeState(int limit);

    private static native String nativeSelect(int index);

    private static native void nativeReset();

    private static native void nativeSaveUserDict();

    public static String selfTest() {
        return nativeVersion() + "\n\n" + nativeSelfTest();
    }

    @Override
    public void load(String dictDir) {
        nativeLoad(dictDir);
    }

    @Override
    public boolean isReady() {
        return nativeIsReady();
    }

    @Override
    public void setShuangpin(boolean flypy) {
        nativeSetShuangpin(flypy);
    }

    @Override
    public void input(char key) {
        nativeInput(key);
    }

    @Override
    public void backspace() {
        nativeBackspace();
    }

    @Override
    public State state(int candidateLimit) {
        return nativeState(candidateLimit);
    }

    @Override
    public String select(int index) {
        return nativeSelect(index);
    }

    @Override
    public void reset() {
        nativeReset();
    }

    @Override
    public void saveUserDict() {
        nativeSaveUserDict();
    }
}
