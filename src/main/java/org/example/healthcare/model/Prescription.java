package org.example.healthcare.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Electronic prescription generated at the end of a consultation.
 *
 * <p>content_encrypted — full prescription text, AES-256 GCM encrypted.
 * Same key strategy as Consultation.notesEncrypted (AWS KMS).
 *
 * <p>pdf_s3_key — S3 object key for the generated PDF. Set asynchronously
 * by a background job after consultation completion.
 */
@Entity
@Table(name = "prescriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** 1:1 — at most one prescription per consultation */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultation_id", nullable = false, unique = true)
    private Consultation consultation;

    /**
     * AES-256 GCM ciphertext of the prescription content.
     * Format: base64(iv || ciphertext || authTag).
     */
    @Column(name = "content_encrypted", nullable = false, columnDefinition = "TEXT")
    private String contentEncrypted;

    /**
     * S3 object key for the signed PDF.
     * Null until the PDF generation job completes.
     */
    @Column(name = "pdf_s3_key")
    private String pdfS3Key;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
