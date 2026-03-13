package com.ncm2flac.core;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Map;

public class MetadataHandler {

    // 写入元数据和封面到音频文件
    public static void writeMetadata(File audioFile, Map<String, Object> metadata, byte[] coverImage) throws Exception {
        AudioFile audioFileIO = AudioFileIO.read(audioFile);
        Tag tag = audioFileIO.getTagOrCreateDefault();

        // 写入基础元数据
        tag.setField(FieldKey.TITLE, (String) metadata.getOrDefault("title", ""));
        tag.setField(FieldKey.ARTIST, (String) metadata.getOrDefault("artist", ""));
        tag.setField(FieldKey.ALBUM, (String) metadata.getOrDefault("album", ""));
        tag.setField(FieldKey.BITRATE, (String) metadata.getOrDefault("bitrate", ""));

        // 写入封面
        if (coverImage != null && coverImage.length > 0) {
            Artwork artwork = ArtworkFactory.createArtworkFromInputStream(new ByteArrayInputStream(coverImage));
            tag.setField(artwork);
        }

        audioFileIO.commit();
    }
}
