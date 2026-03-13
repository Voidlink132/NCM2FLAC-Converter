package com.ncm2flac.core;

import com.ncm2flac.utils.CryptoUtils;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class NcmDecryptor {
    // NCM文件魔数（固定8字节）
    private static final long NCM_MAGIC = 0x4354454E4644414DL;
    private byte[] ncmData;
    private int offset = 0;

    // 解密结果
    private byte[] audioRawData;
    private String audioFormat;
    private Map<String, Object> metadata = new HashMap<>();
    private byte[] coverImage;

    public NcmDecryptor(byte[] ncmData) {
        this.ncmData = ncmData;
    }

    // 执行完整解密流程
    public boolean decrypt() throws Exception {
        // 1. 校验魔数
        if (!checkMagic()) {
            throw new Exception("不是有效的NCM文件");
        }

        // 2. 解密RC4密钥
        byte[] rc4Key = decryptRC4Key();
        if (rc4Key == null || rc4Key.length == 0) {
            throw new Exception("RC4密钥解密失败");
        }

        // 3. 解密元数据
        decryptMetadata();

        // 4. 跳过CRC32和间隙
        offset += 4;
        int gapLen = readInt32();
        offset += gapLen;

        // 5. 解密音频流
        decryptAudioData(rc4Key);

        return true;
    }

    // 校验魔数
    private boolean checkMagic() {
        long magic = readInt64();
        return magic == NCM_MAGIC;
    }

    // 解密RC4密钥
    private byte[] decryptRC4Key() throws Exception {
        offset += 2; // 跳过2字节版本号
        int keyLen = readInt32();
        byte[] encryptedKey = new byte[keyLen];
        System.arraycopy(ncmData, offset, encryptedKey, 0, keyLen);
        offset += keyLen;

        // 异或0x64
        for (int i = 0; i < encryptedKey.length; i++) {
            encryptedKey[i] ^= 0x64;
        }

        // AES解密
        byte[] decryptedKey = CryptoUtils.aes128EcbDecrypt(encryptedKey, CryptoUtils.getNcmCoreKey());
        // 去掉开头的"neteasecloudmusic"字符串
        String keyStr = new String(decryptedKey);
        if (keyStr.startsWith("neteasecloudmusic")) {
            return keyStr.substring(22).getBytes();
        }
        return decryptedKey;
    }

    // 解密元数据
    private void decryptMetadata() throws Exception {
        int metaLen = readInt32();
        if (metaLen <= 0) return;

        byte[] encryptedMeta = new byte[metaLen];
        System.arraycopy(ncmData, offset, encryptedMeta, 0, metaLen);
        offset += metaLen;

        // 异或0x63
        for (int i = 0; i < encryptedMeta.length; i++) {
            encryptedMeta[i] ^= 0x63;
        }

        // Base64解码
        byte[] base64Decoded = Base64.getDecoder().decode(encryptedMeta);
        // AES解密
        byte[] decryptedMeta = CryptoUtils.aes128EcbDecrypt(base64Decoded, CryptoUtils.getNcmMetaKey());
        // 去掉开头的"music:"字符串
        String metaStr = new String(decryptedMeta).substring(6);

        // 解析JSON元数据（简化版，可替换为Gson解析）
        parseMetadata(metaStr);

        // 读取封面
        int coverLen = readInt32();
        if (coverLen > 0) {
            coverImage = new byte[coverLen];
            System.arraycopy(ncmData, offset, coverImage, 0, coverLen);
            offset += coverLen;
        }
    }

    // 解密音频流
    private void decryptAudioData(byte[] rc4Key) {
        int audioLen = ncmData.length - offset;
        audioRawData = new byte[audioLen];
        System.arraycopy(ncmData, offset, audioRawData, 0, audioLen);

        // RC4解密
        audioRawData = CryptoUtils.rc4KeyDecrypt(audioRawData, rc4Key);

        // 识别音频格式
        if (audioRawData[0] == 0x49 && audioRawData[1] == 0x44 && audioRawData[2] == 0x33) {
            audioFormat = "mp3";
        } else if (audioRawData[0] == 0x66 && audioRawData[1] == 0x4C && audioRawData[2] == 0x61 && audioRawData[3] == 0x43) {
            audioFormat = "flac";
        } else {
            audioFormat = "flac"; // 默认flac
        }
    }

    // 简化的元数据解析（可替换为Gson完整解析）
    private void parseMetadata(String metaJson) {
        metadata.put("title", getJsonValue(metaJson, "musicName"));
        metadata.put("artist", getJsonValue(metaJson, "artist"));
        metadata.put("album", getJsonValue(metaJson, "album"));
        metadata.put("bitrate", getJsonValue(metaJson, "bitrate"));
        metadata.put("duration", getJsonValue(metaJson, "duration"));
    }

    private String getJsonValue(String json, String key) {
        String keyStr = "\"" + key + "\":";
        int start = json.indexOf(keyStr);
        if (start == -1) return "";
        start += keyStr.length();
        int end;
        if (json.charAt(start) == '"') {
            start++;
            end = json.indexOf("\"", start);
        } else {
            end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
        }
        return json.substring(start, end).trim();
    }

    // 读取32位小端整数
    private int readInt32() {
        ByteBuffer buffer = ByteBuffer.wrap(ncmData, offset, 4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        offset += 4;
        return buffer.getInt();
    }

    // 读取64位小端整数
    private long readInt64() {
        ByteBuffer buffer = ByteBuffer.wrap(ncmData, offset, 8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        offset += 8;
        return buffer.getLong();
    }

    // Getter方法
    public byte[] getAudioRawData() {
        return audioRawData;
    }

    public String getAudioFormat() {
        return audioFormat;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public byte[] getCoverImage() {
        return coverImage;
    }
}
