package com.clinicdesk.camera;

import com.clinicdesk.exception.CameraUnavailableException;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Abstraction interface for laptop camera hardware acquisition and frame streaming.
 */
public interface CameraService {

    /**
     * Checks whether an accessible camera hardware device is present.
     */
    boolean isCameraAvailable();

    /**
     * Initializes the camera device for acquisition.
     *
     * @param deviceIndex camera device index (0 for default built-in camera)
     * @throws CameraUnavailableException if initialization fails
     */
    void initialize(int deviceIndex) throws CameraUnavailableException;

    /**
     * Starts continuous asynchronous frame acquisition on a background thread.
     *
     * @param frameConsumer consumer dispatched with each new captured frame
     * @throws CameraUnavailableException if stream cannot be started
     */
    void startLiveStream(Consumer<BufferedImage> frameConsumer) throws CameraUnavailableException;

    /**
     * Captures a single snapshot frame from the current stream.
     *
     * @return the raw captured BufferedImage
     * @throws CameraUnavailableException if capture fails
     */
    BufferedImage captureFrame() throws CameraUnavailableException;

    /**
     * Pauses or stops the active live stream without releasing the hardware device.
     */
    void stopLiveStream();

    /**
     * Releases camera device locks and terminates background frame grabbers.
     * Guaranteed to be idempotent and safe to call multiple times.
     */
    void release();
}
