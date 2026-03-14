package com.ncm2flac.core;

import com.ncm2flac.OnConvertListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

public class NcmDecryptCore {

    private static final int NCM_HEADER = 0x4354454E; // "CTEN"
    private static final byte[] AES_KEY_CORE = "hzHRAmso5kInbaxW".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_KEY_META = "#14ljk_!\\]&0U<'(".getBytes(StandardCharsets.UTF_8);
    private static final int BLOCK_SIZE = 0x8000; // 32KB 缓冲
    private final File inputFile;
    private final File outputFile;

    public NcmDecryptCore(File inputFile, String savePath) throws IOException {
        this.inputFile = inputFile;
        File saveDir = new File(savePath);
        if (!saveDir.exists()) saveDir.mkdirs();
        String fileName = inputFile.getName();
        String outputFileName = fileName.replace(".ncm", ".flac");
        this.outputFile = new File(saveDir, outputFileName);
    }

    public void startDecrypt(OnConvertListener listener) {
        if (listener == null) return;
        listener.onStart();

        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {

            // 1. 校验文件头
            byte[] header = new byte[4];
            if (fis.read(header) != 4 || bytesToInt(header) != NCM_HEADER) {
                listener.onFail("无效的NCM文件头");
                return;
            }
            fis.skip(2); // 跳过版本号

            // 2. 解密RC4密钥
            int keyLen = readIntLittleEndian(fis);
            byte[] encryptedKey = new byte[keyLen];
            fis.read(encryptedKey);
            for (int i = 0; i < encryptedKey.length; i++) encryptedKey[i] ^= 0x64;
            byte[] decryptedKey = aesDecrypt(AES_KEY_CORE, encryptedKey);
            if (decryptedKey == null) {
                listener.onFail("AES解密密钥失败");
                return;
            }
            decryptedKey = Arrays.copyOfRange(decryptedKey, 17, decryptedKey.length); // 去掉 "neteasecloudmusic" 前缀
            int[] sBox = generateRC4SBox(decryptedKey);

            // 3. 跳过CRC和垃圾数据
            fis.skip(5);

            // 4. 跳过元数据
            int metaLen = readIntLittleEndian(fis);
            if (metaLen > 0) {
                byte[] encryptedMeta = new byte[metaLen];
                fis.read(encryptedMeta);
                for (int i = 0; i < encryptedMeta.length; i++) encryptedMeta[i] ^= 0x63;
                aesDecrypt(AES_KEY_META, encryptedMeta); // 只解密不解析
            }

            // 5. 计算音频数据的真实大小（用于进度计算）
            long audioStartPos = fis.getChannel().position();
            long audioTotalSize = inputFile.length() - audioStartPos;
            long audioReadSize = 0;

            // 6. 逐块解密音频数据
            byte[] buffer = new byte[BLOCK_SIZE];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                rc4DecryptBlock(sBox, buffer, len);
                fos.write(buffer, 0, len);
                audioReadSize += len;
                // 修正进度：只计算音频数据部分
                int progress = (int) ((audioReadSize * 100) / audioTotalSize);
                listener.onProgress(progress);
            }

            fos.flush();
            listener.onSuccess(outputFile);

        } catch (Exception e) {
            e.printStackTrace(); // 打印日志便于排查
            listener.onFail("解密异常: " + e.getMessage());
            if (outputFile.exists()) outputFile.delete();
        }
    }

    // AES ECB 解密
    private byte[] aesDecrypt(byte[] key, byte[] data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 生成 RC4 S盒（跳过前1024字节）
    private int[] generateRC4SBox(byte[] key) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) s[i] = i;
        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            int temp = s[i]; s[i] = s[j]; s[j] = temp;
        }
        // 跳过前1024次交换
        int x = 0, y = 0;
        for (int i = 0; i < 1024; i++) {
            x = (x + 1) & 0xFF;
            y = (s[x] + y) & 0xFF;
            int temp = s[x]; s[x] = s[y]; s[y] = temp;
        }
        return s;
    }

    // RC4 解密一块数据
    private void rc4DecryptBlock(int[] sBox, byte[] data, int len) {
        int x = 0, y = 0;
        for (int i = 0; i < len; i++) {
            x = (x + 1) & 0xFF;
            y = (sBox[x] + y) & 0xFF;
            int temp = sBox[x]; sBox[x] = sBox[y]; sBox[y] = temp;
            int key = sBox[(sBox[x] + sBox[y]) & 0xFF];
            data[i] ^= (byte) key;
        }
    }

    // 小端序读 int
    private int readIntLittleEndian(FileInputStream fis) throws IOException {
        byte[] bytes = new byte[4];
        fis.read(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    // 大端序转 int（校验文件头）
    private int bytesToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }
}
