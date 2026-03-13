package com.ncm2flac.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SPUtils {
    // 配置文件名称
    private static final String SP_NAME = "ncm2flac_config";
    // LRC歌词下载开关的存储key
    private static final String KEY_LRC_ENABLE = "lrc_enable";
    
    // 线程安全单例
    private static volatile SPUtils instance;
    private final SharedPreferences sp;

    // 私有构造，禁止外部实例化
    private SPUtils(Context context) {
        // 使用ApplicationContext，避免内存泄漏
        sp = context.getApplicationContext()
                .getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
    }

    // 双重校验锁单例
    public static SPUtils getInstance(Context context) {
        if (instance == null) {
            synchronized (SPUtils.class) {
                if (instance == null) {
                    instance = new SPUtils(context);
                }
            }
        }
        return instance;
    }

    /**
     * 保存LRC歌词自动下载开关状态
     */
    public void setLrcEnable(boolean enable) {
        sp.edit().putBoolean(KEY_LRC_ENABLE, enable).apply();
    }

    /**
     * 获取LRC歌词自动下载开关状态
     */
    public boolean isLrcEnable() {
        return sp.getBoolean(KEY_LRC_ENABLE, true);
    }

    /**
     * 清空所有配置
     */
    public void clearAll() {
        sp.edit().clear().apply();
    }
}
