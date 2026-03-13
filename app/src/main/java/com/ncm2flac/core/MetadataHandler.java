package com.ncm2flac.core;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import java.io.File;
import java.util.Map;

public class MetadataHandler {
    // 写入歌曲元数据到音频文件，极简稳定版，无API兼容问题
    public static void writeMetadata(File audioFile, Map<String, Object> metadata) {
        try {
            // 校验文件有效性
            if (!audioFile.exists() || audioFile.length() == 0) {
                return;
            }
            // 读取音频文件
            AudioFile af = AudioFileIO.read(audioFile);
            Tag tag = af.getTagOrCreateDefault();

            // 写入核心元数据
            tag.setField(FieldKey.TITLE, metadata.getOrDefault("title", "未知歌曲").toString());
            tag.setField(FieldKey.ARTIST, metadata.getOrDefault("artist", "未知歌手").toString());
            tag.setField(FieldKey.ALBUM, metadata.getOrDefault("album", "未知专辑").toString());

            // 提交修改到文件
            af.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
