package com.kbd.ime;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 引擎数据文件的首次解压：assets/engine/* → filesDir/engine/*。
 */
final class EngineData {

    private static final String ASSET_DIR = "engine";
    private static final String DIR = "engine";

    private EngineData() {
    }

    /** 返回可用数据目录；若已完整则直接返回。 */
    static File ensure(Context context) throws IOException {
        File dir = new File(context.getFilesDir(), DIR);
        File dict = new File(dir, "sc.dict");
        File lm = new File(dir, "sc.lm");

        if (isUsable(dict) && isUsable(lm)) {
            return dir;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("无法创建目录：" + dir);
        }
        copyAsset(context, "sc.dict", dict);
        copyAsset(context, "sc.lm", lm);
        return dir;
    }

    private static boolean isUsable(File file) {
        return file.isFile() && file.length() > 0;
    }

    private static void copyAsset(Context context, String name, File dest)
            throws IOException {
        File tmp = new File(dest.getParentFile(), name + ".tmp");
        try (InputStream in = context.getAssets().open(ASSET_DIR + "/" + name);
             OutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
        if (dest.exists() && !dest.delete()) {
            throw new IOException("无法覆盖：" + dest);
        }
        if (!tmp.renameTo(dest)) {
            throw new IOException("无法重命名：" + tmp);
        }
    }
}
