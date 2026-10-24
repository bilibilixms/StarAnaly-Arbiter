package com.staranaly.arbiter.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "xiaohongshu_note_extra_images")
public class XiaohongshuNoteExtraImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "note_id", nullable = false)
    private Long noteId;
    @Column(nullable = false)
    private int position;
    @Column(name = "image_name", nullable = false)
    private String imageName;
    @Column(name = "image_mime", nullable = false, length = 30)
    private String imageMime;
    @Column(name = "image_size", nullable = false)
    private int imageSize;
    @Lob @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    protected XiaohongshuNoteExtraImage() {}
    public XiaohongshuNoteExtraImage(long noteId, int position, String name, String mime, byte[] data) {
        this.noteId = noteId;
        this.position = position;
        this.imageName = name;
        this.imageMime = mime;
        this.imageSize = data.length;
        this.imageData = data;
    }
    public Long getId() { return id; }
    public Long getNoteId() { return noteId; }
    public String getImageName() { return imageName; }
    public String getImageMime() { return imageMime; }
    public int getImageSize() { return imageSize; }
    public byte[] getImageData() { return imageData; }
}
