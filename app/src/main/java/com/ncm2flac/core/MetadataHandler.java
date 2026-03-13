package com.ncm2flac.core;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import java.io.File;
import java.util.Map;

public class MetadataHandler {
    // 参考ncmc的元数据写入逻辑，仅保留核心字段，无API兼容问题
    public static void writeMetadata(File audioFile, Map<String, Object> metadata, byte[] coverImage) {
        try {
            if (!audioFile.exists() || audioFile.length() == 0) {
                return;
            }
            AudioFile af = AudioFileIO.read(audioFile);
            Tag tag = af.getTagOrCreateDefault();

            // 写入核心元数据（和ncmc一致的字段）
            tag.setField(FieldKey.TITLE, metadata.getOrDefault("title", "未知歌曲").toString());
            tag.setField(FieldKey.ARTIST, metadata.getOrDefault("artist", "未知歌手").toString());
            tag.setField(FieldKey.ALBUM, metadata.getOrDefault("album", "未知专辑").toString());

            // 写入封面（jaudiotagger 3.0.1 标准写法，参考ncmc的封面处理）
            if (coverImage != null && coverImage.length > 100) {
                Artwork artwork = Artwork.createArtwork(coverImage);
                artwork.setMimeType("image/jpeg");
                artwork.setPictureType(Artwork.DEFAULT_PICTURE_TYPE);
                tag.setField(artwork);
            }

            af.commit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
