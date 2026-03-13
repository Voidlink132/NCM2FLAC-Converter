package com.ncm2flac.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FileUtils {
    // 网易云音乐默认下载目录
    public static final String NETEASE_MUSIC_DIR = "/Download/netease/cloudmusic/Music/";

    /**
     * 从Uri读取文件为字节数组
     */
    public static byte[] readUriToBytes(Context context, Uri uri) throws Exception {
        InputStream is = context.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 8];
        int len;
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        is.close();
        bos.close();
        return bos.toByteArray();
    }

    /**
     * 读取本地文件为字节数组
     */
    public static byte[] readFileToBytes(File file) throws Exception {
        FileInputStream fis = new FileInputStream(file);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 8];
        int len;
        while ((len = fis.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        fis.close();
        bos.close();
        return bos.toByteArray();
    }

    /**
     * 写入字节数组到文件
     */
    public static void writeBytesToFile(byte[] data, File file) throws Exception {
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(data);
        fos.flush();
        fos.close();
    }

    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }

    /**
     * 替换文件扩展名
     */
    public static String replaceFileExtension(String fileName, String newExt) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex == -1) {
            return fileName + "." + newExt;
        }
        return fileName.substring(0, dotIndex) + "." + newExt;
    }

    /**
     * 从Uri获取真实文件名
     */
    public static String getFileNameFromUri(Context context, Uri uri) {
        String fileName = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                    if (index != -1) {
                        fileName = cursor.getString(index);
                    }
                }
                cursor.close();
            }
        }
        if (fileName == null) {
            fileName = uri.getLastPathSegment();
        }
        return fileName;
    }

    /**
     * 扫描网易云下载目录下的所有NCM文件
     */
    public static List<File> scanNcmFiles() {
        List<File> ncmFileList = new ArrayList<>();
        File musicDir = new File(Environment.getExternalStorageDirectory(), NETEASE_MUSIC_DIR);
        // 目录不存在直接返回空列表
        if (!musicDir.exists() || !musicDir.isDirectory()) {
            return ncmFileList;
        }
        File[] files = musicDir.listFiles();
        if (files == null) return ncmFileList;
        // 遍历筛选.ncm文件
        for (File file : files) {
            if (file.isFile() && getFileExtension(file.getName()).equals("ncm")) {
                ncmFileList.add(file);
            }
        }
        return ncmFileList;
    }

    /**
     * 格式化文件大小，转为易读格式
     */
    public static String formatFileSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    /**
     * 获取APP输出目录
     */
    public static File getOutputDir(Context context) {
        File outputDir = new File(context.getExternalFilesDir(null), "NCM2FLAC");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }
        return outputDir;
    }
}
