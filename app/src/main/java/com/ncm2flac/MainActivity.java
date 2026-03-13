private void startDecrypt() {
    tvStatus.setText("正在验证NCM文件...");
    new Thread(() -> {
        try {
            // 1. 读取文件
            byte[] ncmData = FileUtils.readUriToBytes(this, selectedFileUri);
            NcmDecryptor decryptor = new NcmDecryptor(ncmData);
            
            // 2. 解密校验
            if (!decryptor.decrypt()) {
                runOnUiThread(() -> {
                    tvStatus.setText("解析失败：不是有效的NCM文件");
                    Toast.makeText(this, "文件无效，请选择标准NCM文件", Toast.LENGTH_LONG).show();
                });
                return;
            }

            // 3. 生成输出文件
            String fileName = FileUtils.getFileNameFromUri(this, selectedFileUri);
            String outputFileName = FileUtils.replaceFileExtension(fileName, decryptor.getAudioFormat());
            File outputDir = new File(getExternalFilesDir(null), "Ncm2Flac");
            if (!outputDir.exists()) outputDir.mkdirs();
            File outputFile = new File(outputDir, outputFileName);

            // 4. 写入音频文件
            FileUtils.writeBytesToFile(decryptor.getAudioRawData(), outputFile);

            // 5. 写入元数据（适配修改后的方法）
            MetadataHandler.writeMetadata(outputFile, decryptor.getMetadata());

            // 6. 更新UI
            runOnUiThread(() -> {
                tvStatus.setText("转换完成！\n保存路径：" + outputFile.getAbsolutePath());
                Toast.makeText(this, "转换成功", Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            runOnUiThread(() -> {
                tvStatus.setText("转换失败：" + e.getMessage());
                Toast.makeText(this, "失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
            });
            e.printStackTrace();
        }
    }).start();
}
