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

    // NCM文件固定头标识
    private static final int NCM_HEADER = 0x4354454E; // "CTEN"
    // NCM格式固定AES密钥（来自开源ncmc-web标准实现）
    private static final byte[] AES_KEY_CORE = "hzHRAmso5kInbaxW".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AES_KEY_META = "#14ljk_!\\]&0U<'(".getBytes(StandardCharsets.UTF_8);
    // 音频解密块大小
    private static final int BLOCK_SIZE = 0x8000;
    // 输入输出文件
    private final File inputFile;
    private final File outputFile;

    public NcmDecryptCore(File inputFile, String savePath) throws IOException {
        this.inputFile = inputFile;
        // 确保输出目录存在
        File saveDir = new File(savePath);
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        // 生成输出文件名，自动识别后缀
        String fileName = inputFile.getName();
        String outputFileName = fileName.substring(0, fileName.lastIndexOf(".")) + ".flac";
        this.outputFile = new File(saveDir, outputFileName);
    }

    public void startDecrypt(OnConvertListener listener) {
        if (listener == null) return;

        listener.onStart();
        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(inputFile);
            fos = new FileOutputStream(outputFile);

            // 1. 验证NCM文件头
            byte[] header = new byte[4];
            if (fis.read(header) != 4 || bytesToInt(header) != NCM_HEADER) {
                listener.onFail("不是有效的NCM格式文件");
                return;
            }

            // 2. 跳过2字节版本信息
            fis.skip(2);

            // 3. 读取并解密RC4核心密钥
            int keyLen = readIntLittleEndian(fis);
            byte[] encryptedKey = new byte[keyLen];
            fis.read(encryptedKey);
            // 对密钥数据做异或预处理
            for (int i = 0; i < encryptedKey.length; i++) {
                encryptedKey[i] ^= 0x64;
            }
            // AES解密核心密钥
            byte[] decryptedKey = aesDecrypt(AES_KEY_CORE, encryptedKey);
            if (decryptedKey == null) {
                listener.onFail("密钥解密失败");
                return;
            }
            // 去掉前缀"neteasecloudmusic"
            decryptedKey = Arrays.copyOfRange(decryptedKey, 17, decryptedKey.length);

            // 4. 生成RC4 S盒
            int[] sBox = generateRC4SBox(decryptedKey);

            // 5. 跳过CRC和无关数据
            fis.skip(5);

            // 6. 读取并解密元数据（可选，不影响音频播放）
            int metaLen = readIntLittleEndian(fis);
            if (metaLen > 0) {
                byte[] encryptedMeta = new byte[metaLen];
                fis.read(encryptedMeta);
                for (int i = 0; i < encryptedMeta.length; i++) {
                    encryptedMeta[i] ^= 0x63;
                }
                // 解密元数据，这里不需要解析，跳过即可
                aesDecrypt(AES_KEY_META, encryptedMeta);
            }

            // 7. 计算文件总大小，用于进度更新
            long totalSize = inputFile.length();
            long readSize = fis.getChannel().position();

            // 8. 核心：RC4解密音频数据
            byte[] buffer = new byte[BLOCK_SIZE];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                // 逐块解密音频数据
                rc4DecryptBlock(sBox, buffer, len);
                // 写入解密后的音频数据
                fos.write(buffer, 0, len);
                // 更新进度
                readSize += len;
                int progress = (int) ((readSize * 100) / totalSize);
                listener.onProgress(progress);
            }

            // 刷新流，确保数据全部写入
            fos.flush();
            listener.onSuccess(outputFile);

        } catch (Exception e) {
            // 异常捕获，回调失败信息
            listener.onFail("解密失败：" + e.getMessage());
            // 转换失败时删除不完整的输出文件
            if (outputFile.exists()) {
                outputFile.delete();
            }
        } finally {
            // 关闭流，避免内存泄漏
            try {
                if (fis != null) fis.close();
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // AES ECB模式解密，PKCS5Padding填充，标准NCM解密实现
    private byte[] aesDecrypt(byte[] key, byte[] data) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException e) {
            e.printStackTrace();
            return null;
        }
    }

    // 生成RC4 S盒，标准NCM实现，跳过前1024字节
    private int[] generateRC4SBox(byte[] key) {
        int[] s = new int[256];
        for (int i = 0; i < 256; i++) {
            s[i] = i;
        }

        int j = 0;
        for (int i = 0; i < 256; i++) {
            j = (j + s[i] + (key[i % key.length] & 0xFF)) & 0xFF;
            // 交换s[i]和s[j]
            int temp = s[i];
            s[i] = s[j];
            s[j] = temp;
        }

        // 跳过前1024个字节，NCM标准要求
        int x = 0, y = 0;
        for (int i = 0; i < 1024; i++) {
            x = (x + 1) & 0xFF;
            y = (s[x] + y) & 0xFF;
            int temp = s[x];
            s[x] = s[y];
            s[y] = temp;
        }

        return s;
    }

    // RC4解密单个数据块
    private void rc4DecryptBlock(int[] sBox, byte[] data, int len) {
        int x = 0, y = 0;
        for (int i = 0; i < len; i++) {
            x = (x + 1) & 0xFF;
            y = (sBox[x] + y) & 0xFF;
            // 交换sBox[x]和sBox[y]
            int temp = sBox[x];
            sBox[x] = sBox[y];
            sBox[y] = temp;
            // 异或解密
            int key = sBox[(sBox[x] + sBox[y]) & 0xFF];
            data[i] ^= (byte) key;
        }
    }

    // 小端序读取4字节int
    private int readIntLittleEndian(FileInputStream fis) throws IOException {
        byte[] bytes = new byte[4];
        fis.read(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    // 字节数组转int（大端序，用于验证文件头）
    private int bytesToInt(byte[] bytes) {
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }
}
