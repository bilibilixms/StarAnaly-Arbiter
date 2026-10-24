package com.staranaly.arbiter.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "douyin_note_images")
public class DouyinNoteImage {
    @Id
    @Column(name = "note_id")
    private Long noteId;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    protected DouyinNoteImage() {}

    public DouyinNoteImage(Long noteId, byte[] imageData) {
        this.noteId = noteId;
        this.imageData = imageData;
    }

    public byte[] getImageData() { return imageData; }
    public void setImageData(byte[] imageData) { this.imageData = imageData; }
}
