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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * CameraService implementation supporting real-time hardware laptop camera capture
 * via JavaCV / OpenCV, with an automated fallback generator for non-hardware environments.
 */
public class OpenCVCameraService implements CameraService {

    private static final Logger LOGGER = AppLogger.getLogger(OpenCVCameraService.class);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private ScheduledExecutorService frameExecutor;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);
    private volatile BufferedImage latestFrame;

    private OpenCVFrameGrabber grabber;
    private Java2DFrameConverter converter;
    private int deviceIndex = 0;
    private boolean hardwareActive = false;

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

        // Try initializing native OpenCV hardware grabber
        try {
            LOGGER.info("Attempting to connect to hardware camera at index " + deviceIndex + "...");
            grabber = new OpenCVFrameGrabber(deviceIndex);
            grabber.setImageWidth(1280);
            grabber.setImageHeight(720);
            grabber.start();
            converter = new Java2DFrameConverter();
            hardwareActive = true;
            LOGGER.info("Connected successfully to native laptop camera (deviceIndex=" + deviceIndex + ")");
        } catch (Throwable t) {
            LOGGER.warning("Could not connect to hardware camera: " + t.getMessage() + ". Using fallback simulator.");
            hardwareActive = false;
            if (grabber != null) {
                try { grabber.stop(); grabber.release(); } catch (Exception ignored) {}
                grabber = null;
            }
        }

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
                        if (f != null && converter != null) {
                            frame = converter.convert(f);
                        }
                    } catch (Throwable t) {
                        LOGGER.warning("Hardware frame grab failed: " + t.getMessage());
                        hardwareActive = false;
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
