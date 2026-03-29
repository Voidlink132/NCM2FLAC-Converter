package com.musicdecrypter.utils;

import android.util.Base64;
import android.webkit.JavascriptInterface;

public class DecryptBridge {
    private final DecryptCallback callback;

    public interface DecryptCallback {
        void onDecryptSuccess(String fileName, byte[] fileData);
        void onDecryptFailed(String errorMsg);
    }

    public DecryptBridge(DecryptCallback callback) {
        this.callback = callback;
    }

    @JavascriptInterface
    public void onDecryptSuccess(String fileName, String base64Data) {
        try {
            byte[] fileData = Base64.decode(base64Data, Base64.DEFAULT);
            if (callback != null) {
                callback.onDecryptSuccess(fileName, fileData);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onDecryptFailed("文件解析失败：" + e.getMessage());
            }
        }
    }

    @JavascriptInterface
    public void onDecryptFailed(String errorMsg) {
        if (callback != null) {
            callback.onDecryptFailed(errorMsg);
        }
    }
}
