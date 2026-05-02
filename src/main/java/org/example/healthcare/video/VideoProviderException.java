package org.example.healthcare.video;

/**
 * Unchecked exception thrown when a {@link VideoProvider} operation fails.
 *
 * <p>Callers should not catch this in normal flow — let it propagate so that
 * Quartz/Spring can retry the job or the controller returns 500 (and Daily
 * retries the webhook).
 */
public class VideoProviderException extends RuntimeException {

    public VideoProviderException(String message) {
        super(message);
    }

    public VideoProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
