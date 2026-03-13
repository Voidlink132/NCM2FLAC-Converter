package com.ncm2flac.core;

import com.ncm2flac.utils.CryptoUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NcmDecryptor {
    // 【对齐ncmc】标准NCM魔数：8字节 "CTENFDAM"
    private static final long NCM_MAGIC = 0x4354454E4644414DL;
    private static final Pattern META_PATTERN = Pattern.compile("\\{.*\\}");

    private final ByteBuffer ncmBuffer;
    private byte[] audioRawData;
    private String audioFormat;
    private Map<String, Object> metadata = new HashMap<>();
    private byte[] coverImage;

    public NcmDecryptor(byte[] ncmData) {
        this.ncmBuffer = ByteBuffer.wrap(ncmData);
        this.ncmBuffer.order(ByteOrder.LITTLE_ENDIAN); // 【对齐ncmc】小端序解析
    }

    // 【完全对齐ncmc的解密流程】
    public boolean decrypt() {
        try {
            // 1. 魔数校验（ncmc第一步校验，不通过直接判定无效文件）
            if (ncmBuffer.getLong() != NCM_MAGIC) {
                throw new IllegalArgumentException("魔数校验失败，不是标准NCM文件");
            }

            // 2. 跳过2字节版本号（ncmc标准步骤）
            ncmBuffer.position(ncmBuffer.position() + 2);

            // 3. 解密RC4密钥（对齐ncmc的AES-ECB解密逻辑）
            int keyLen = ncmBuffer.getInt();
            byte[] encKey = new byte[keyLen];
            ncmBuffer.get(encKey);
            // 【对齐ncmc】所有字节异或0x64
            for (int i = 0; i < encKey.length; i++) {
                encKey[i] ^= 0x64;
            }
            // AES解密
            byte[] decKey = CryptoUtils.aes128EcbDecrypt(encKey, CryptoUtils.getNcmCoreKey());
            // 【修复核心错误】对齐ncmc，去掉开头17字节的"neteasecloudmusic"，不是22字节
            byte[] rc4Key = new String(decKey).substring(17).getBytes();

            // 4. 解密元数据（对齐ncmc的元数据解析逻辑）
            int metaLen = ncmBuffer.getInt();
            if (metaLen > 0) {
                byte[] encMeta = new byte[metaLen];
                ncmBuffer.get(encMeta);
                // 【对齐ncmc】所有字节异或0x63
                for (int i = 0; i < encMeta.length; i++) {
                    encMeta[i] ^= 0x63;
                }
                // Base64解码 + AES解密
                byte[] base64Meta = Base64.getDecoder().decode(encMeta);
                byte[] decMeta = CryptoUtils.aes128EcbDecrypt(base64Meta, CryptoUtils.getNcmMetaKey());
                // 去掉开头6字节的"music:"
                String metaStr = new String(decMeta).substring(6);
                // 提取JSON元数据
                Matcher matcher = META_PATTERN.matcher(metaStr);
                if (matcher.find()) {
                    metaStr = matcher.group(0);
                    metadata.put("title", getJsonValue(metaStr, "musicName"));
                    metadata.put("artist", getJsonValue(metaStr, "artist"));
                    metadata.put("album", getJsonValue(metaStr, "album"));
                }
            }

            // 5. 跳过CRC32和预留间隙（对齐ncmc标准偏移）
            ncmBuffer.position(ncmBuffer.position() + 4); // 跳过CRC32
            int gapLen = ncmBuffer.getInt();
            ncmBuffer.position(ncmBuffer.position() + gapLen); // 跳过预留间隙

            // 6. 读取封面图片（对齐ncmc）
            int coverLen = ncmBuffer.getInt();
            if (coverLen > 0 && coverLen < ncmBuffer.remaining()) {
                coverImage = new byte[coverLen];
                ncmBuffer.get(coverImage);
            }

            // 7. 解密音频流（对齐ncmc的RC4解密）
            int audioLen = ncmBuffer.remaining();
            byte[] encAudio = new byte[audioLen];
            ncmBuffer.get(encAudio);
            audioRawData = CryptoUtils.rc4KeyDecrypt(encAudio, rc4Key);

            // 8. 识别音频格式（对齐ncmc的自动识别逻辑）
            identifyAudioFormat();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 【对齐ncmc】音频格式自动识别
    private void identifyAudioFormat() {
        if (audioRawData == null || audioRawData.length < 4) {
            audioFormat = "flac";
            return;
        }
        // FLAC头：fLaC 0x66 0x4C 0x61 0x43
        if (audioRawData[0] == 0x66 && audioRawData[1] == 0x4C && audioRawData[2] == 0x61 && audioRawData[3] == 0x43) {
            audioFormat = "flac";
        }
        // MP3头：ID3 0x49 0x44 0x33
        else if (audioRawData[0] == 0x49 && audioRawData[1] == 0x44 && audioRawData[2] == 0x33) {
            audioFormat = "mp3";
        }
        // 兜底默认flac
        else {
            audioFormat = "flac";
        }
    }

    // 简易JSON值提取，对齐ncmc的元数据提取逻辑
    private String getJsonValue(String json, String key) {
        String keyStr = "\"" + key + "\":";
        int start = json.indexOf(keyStr);
        if (start == -1) return "未知";
        start += keyStr.length();
        if (json.charAt(start) == '[') {
            int end = json.indexOf(']', start) + 1;
            return json.substring(start, end).replace("[", "").replace("]", "").replace("\"", "");
        } else if (json.charAt(start) == '"') {
            start++;
            int end = json.indexOf("\"", start);
            return json.substring(start, end);
        } else {
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end);
        }
    }

    // Getter方法
    public byte[] getAudioRawData() { return audioRawData; }
    public String getAudioFormat() { return audioFormat; }
    public Map<String, Object> getMetadata() { return metadata; }
    public byte[] getCoverImage() { return coverImage; }
}
