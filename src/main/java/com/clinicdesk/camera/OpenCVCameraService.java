package com.clinicdesk.camera;

import com.clinicdesk.exception.CameraUnavailableException;
import com.clinicdesk.logging.AppLogger;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CameraService implementation supporting real-time hardware laptop camera capture
 * via JavaCV / OpenCV, with an automated fallback generator for non-hardware environments.
 *
 * <p>Improvements over baseline:
 * <ul>
 *   <li>Dynamic resolution — no hardcoded width/height before {@code grabber.start()}</li>
 *   <li>Warm-up retry loop for sensor auto-exposure initialization</li>
 *   <li>Consecutive failure counter — single frame drops no longer permanently disable hardware</li>
 *   <li>Device index fallback (tries index 1 if index 0 fails)</li>
 * </ul>
 */
public class OpenCVCameraService implements CameraService {

    private static final Logger LOGGER = AppLogger.getLogger(OpenCVCameraService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** Max consecutive grab failures before falling back to synthetic frames */
    private static final int MAX_CONSECUTIVE_FAILURES = 15;

    private ScheduledExecutorService frameExecutor;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private volatile BufferedImage latestFrame;

    private OpenCVFrameGrabber grabber;
    private Java2DFrameConverter converter;
    private int deviceIndex = 0;
    private volatile boolean hardwareActive = false;

    /** Consecutive frame-grab failure counter; reset on each successful grab */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    public OpenCVCameraService() {
    }

    @Override
    public boolean isCameraAvailable() {
        return true;
    }

    @Override
    public void initialize(int deviceIndex) throws CameraUnavailableException {
        this.deviceIndex = deviceIndex;
        LOGGER.info("Initializing OpenCV Camera (deviceIndex=" + deviceIndex + ")");
    }

    @Override
    public synchronized void startLiveStream(Consumer<BufferedImage> frameConsumer) throws CameraUnavailableException {
        if (isStreaming.get()) {
            return;
        }

        // Attempt hardware camera connection with device-index fallback
        hardwareActive = tryConnectCamera(deviceIndex);
        if (!hardwareActive && deviceIndex == 0) {
            LOGGER.info("Device index 0 failed; attempting fallback to device index 1...");
            hardwareActive = tryConnectCamera(1);
        }

        if (hardwareActive) {
            LOGGER.info("Hardware camera active — resolution: " +
                    grabber.getImageWidth() + "x" + grabber.getImageHeight());
        } else {
            LOGGER.warning("No hardware camera available. Using synthetic fallback.");
        }

        consecutiveFailures.set(0);
        isStreaming.set(true);
        frameExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Camera-Frame-Grabber");
            t.setDaemon(true);
            return t;
        });

        // 30 FPS stream loop (~33 ms per frame)
        frameExecutor.scheduleAtFixedRate(() -> {
            if (!isStreaming.get()) {
                return;
            }
            try {
                BufferedImage frame = null;
                if (hardwareActive && grabber != null) {
                    try {
                        Frame f = grabber.grab();
                        if (f != null && f.image != null && converter != null) {
                            frame = converter.convert(f);
                        }
                    } catch (Throwable t) {
                        // Don't log every transient drop at WARNING — use FINE
                        LOGGER.log(Level.FINE, "Transient hardware frame grab issue: " + t.getMessage());
                    }

                    if (frame != null) {
                        // Success — reset failure counter
                        consecutiveFailures.set(0);
                    } else {
                        int failures = consecutiveFailures.incrementAndGet();
                        if (failures >= MAX_CONSECUTIVE_FAILURES) {
                            LOGGER.warning("Hardware camera exceeded " + MAX_CONSECUTIVE_FAILURES +
                                    " consecutive frame failures — switching to synthetic fallback.");
                            hardwareActive = false;
                        }
                    }
                }

                if (frame == null) {
                    frame = generateNextFrame();
                }

                latestFrame = frame;
                if (frameConsumer != null) {
                    frameConsumer.accept(frame);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Frame acquisition error", e);
            }
        }, 0, 33, TimeUnit.MILLISECONDS);

        LOGGER.info("Camera live stream started at ~30 FPS (hardwareActive=" + hardwareActive + ").");
    }

    /**
     * Attempts to connect to a camera at the given device index.
     * Uses dynamic resolution (no hardcoded width/height) and a warm-up loop.
     *
     * @return {@code true} if the camera connected and produced at least one valid frame
     */
    private boolean tryConnectCamera(int index) {
        OpenCVFrameGrabber candidate = null;
        try {
            LOGGER.info("Attempting to connect to hardware camera at index " + index + "...");
            candidate = new OpenCVFrameGrabber(index);

            // Do NOT hardcode resolution — let the camera negotiate its native default
            candidate.start();

            // Warm-up loop: discard up to 10 blank/black initialization frames
            Java2DFrameConverter warmUpConverter = new Java2DFrameConverter();
            boolean warmUpSuccess = false;
            for (int i = 0; i < 10; i++) {
                try {
                    Frame f = candidate.grab();
                    if (f != null && f.image != null) {
                        warmUpSuccess = true;
                        break;
                    }
                } catch (Exception ignored) {
                    // Sensor may throw during auto-exposure initialization
                }
                Thread.sleep(50);
            }

            if (!warmUpSuccess) {
                LOGGER.warning("Camera at index " + index + " started but produced no valid frames during warm-up.");
                try { candidate.stop(); candidate.release(); } catch (Exception ignored) {}
                return false;
            }

            // Success — adopt this grabber as the active one
            if (this.grabber != null) {
                try { this.grabber.stop(); this.grabber.release(); } catch (Exception ignored) {}
            }
            this.grabber = candidate;
            this.converter = warmUpConverter;
            this.deviceIndex = index;

            LOGGER.info("Connected successfully to native laptop camera (deviceIndex=" + index +
                    ", resolution=" + candidate.getImageWidth() + "x" + candidate.getImageHeight() + ")");
            return true;
        } catch (Throwable t) {
            LOGGER.warning("Could not connect to hardware camera at index " + index + ": " + t.getMessage());
            if (candidate != null) {
                try { candidate.stop(); candidate.release(); } catch (Exception ignored) {}
            }
            return false;
        }
    }

    @Override
    public BufferedImage captureFrame() throws CameraUnavailableException {
        if (latestFrame == null) {
            latestFrame = generateNextFrame();
        }
        // Deep copy the snapshot
        int type = latestFrame.getType() > 0 ? latestFrame.getType() : BufferedImage.TYPE_INT_RGB;
        BufferedImage copy = new BufferedImage(latestFrame.getWidth(), latestFrame.getHeight(), type);
        Graphics2D g = copy.createGraphics();
        g.drawImage(latestFrame, 0, 0, null);
        g.dispose();
        LOGGER.info("Snapshot frame captured: " + copy.getWidth() + "x" + copy.getHeight());
        return copy;
    }

    @Override
    public void stopLiveStream() {
        if (isStreaming.compareAndSet(true, false)) {
            if (frameExecutor != null && !frameExecutor.isShutdown()) {
                frameExecutor.shutdownNow();
            }
            if (grabber != null) {
                try {
                    grabber.stop();
                    grabber.release();
                    LOGGER.info("Native hardware webcam released.");
                } catch (Throwable t) {
                    LOGGER.warning("Error releasing OpenCV grabber: " + t.getMessage());
                }
                grabber = null;
            }
            if (converter != null) {
                try { converter.close(); } catch (Exception ignored) {}
                converter = null;
            }
            hardwareActive = false;
            consecutiveFailures.set(0);
            LOGGER.info("Camera live stream stopped.");
        }
    }

    @Override
    public synchronized void release() {
        stopLiveStream();
        latestFrame = null;
        LOGGER.info("Camera resources released successfully.");
    }

    private BufferedImage generateNextFrame() {
        int width = 720;
        int height = 540;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Dark room background
            g2.setColor(new Color(24, 32, 47));
            g2.fillRect(0, 0, width, height);

            // Simulated white prescription sheet in center
            int cardW = 460;
            int cardH = 360;
            int cardX = (width - cardW) / 2;
            int cardY = (height - cardH) / 2;

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(cardX, cardY, cardW, cardH, 12, 12);

            // Header line
            g2.setColor(new Color(2, 132, 199));
            g2.fillRoundRect(cardX, cardY, cardW, 40, 12, 12);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.drawString("CLINICDESK — MEDICAL PRESCRIPTION", cardX + 24, cardY + 26);

            // Rx Symbol and clinical content simulation
            g2.setColor(new Color(15, 23, 42));
            g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 32));
            g2.drawString("℞", cardX + 30, cardY + 80);

            g2.setColor(new Color(100, 116, 139));
            g2.setFont(new Font("SansSerif", Font.PLAIN, 13));
            g2.drawString("Patient: [Camera Device In Use / Fallback]", cardX + 75, cardY + 75);
            g2.drawString("Date / Time: " + LocalDateTime.now().format(TIME_FMT), cardX + 75, cardY + 95);

            // Rule lines
            g2.setColor(new Color(226, 232, 240));
            for (int y = cardY + 130; y < cardY + cardH - 50; y += 28) {
                g2.drawLine(cardX + 30, y, cardX + cardW - 30, y);
            }

            // Doctor signature stamp simulation
            g2.setColor(new Color(30, 41, 59));
            g2.setFont(new Font("SansSerif", Font.ITALIC, 14));
            g2.drawString("Attending Physician Signature: __________________", cardX + 40, cardY + cardH - 24);

            // Alignment target guides (corners)
            g2.setColor(new Color(2, 132, 199));
            int markSize = 18;
            g2.drawLine(cardX, cardY, cardX + markSize, cardY);
            g2.drawLine(cardX, cardY, cardX, cardY + markSize);
            g2.drawLine(cardX + cardW, cardY, cardX + cardW - markSize, cardY);
            g2.drawLine(cardX + cardW, cardY, cardX + cardW, cardY + markSize);
            g2.drawLine(cardX, cardY + cardH, cardX + markSize, cardY + cardH);
            g2.drawLine(cardX, cardY + cardH, cardX, cardY + cardH - markSize);
            g2.drawLine(cardX + cardW, cardY + cardH, cardX + cardW - markSize, cardY + cardH);
            g2.drawLine(cardX + cardW, cardY + cardH, cardX + cardW, cardY + cardH - markSize);

        } finally {
            g2.dispose();
        }
        return img;
    }
}
