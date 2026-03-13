package com.ncm2flac.core;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;

public class MetadataHandler {

    // 写入元数据和封面到音频文件（适配jaudiotagger 3.0.1 API）
    public static void writeMetadata(File audioFile, Map<String, Object> metadata, byte[] coverImage) throws Exception {
        AudioFile audioFileIO = AudioFileIO.read(audioFile);
        Tag tag = audioFileIO.getTagOrCreateDefault();

        // 写入核心元数据（移除了不存在的BITRATE字段）
        tag.setField(FieldKey.TITLE, (String) metadata.getOrDefault("title", "未知歌曲"));
        tag.setField(FieldKey.ARTIST, (String) metadata.getOrDefault("artist", "未知歌手"));
        tag.setField(FieldKey.ALBUM, (String) metadata.getOrDefault("album", "未知专辑"));

        // 写入封面图片（适配新版API）
        if (coverImage != null && coverImage.length > 0) {
            Artwork artwork = Artwork.createArtwork(new ByteArrayInputStream(coverImage), "image/jpeg");
            tag.setField(artwork);
        }

        // 提交写入到文件
        audioFileIO.commit();
    }
}
