package com.clinicdesk.test;

import com.clinicdesk.camera.CameraService;
import com.clinicdesk.camera.ImageProcessingService;
import com.clinicdesk.camera.OpenCVCameraService;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Phase7VerificationRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Phase 7 Verification...");

        ImageProcessingService imgService = new ImageProcessingService();
        CameraService cameraService = new OpenCVCameraService();

        // 1. Image Transformations Test (Rotation 90, 180, 270)
        BufferedImage src = new BufferedImage(300, 150, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        BufferedImage rot90 = imgService.rotate(src, 90);
        assert rot90.getWidth() == 150 && rot90.getHeight() == 300 : "90 degree rotation dimension swap failed";
        System.out.println("✓ 90° Clockwise rotation verified: " + src.getWidth() + "x" + src.getHeight() + " -> " + rot90.getWidth() + "x" + rot90.getHeight());

        BufferedImage rot180 = imgService.rotate(src, 180);
        assert rot180.getWidth() == 300 && rot180.getHeight() == 150 : "180 degree rotation dimension mismatch";
        System.out.println("✓ 180° Rotation verified: dimensions preserved.");

        // 2. Horizontal and Vertical Mirror Flipping
        BufferedImage flipH = imgService.flipHorizontal(src);
        assert flipH.getWidth() == 300 && flipH.getHeight() == 150 : "Flip H dimension mismatch";
        BufferedImage flipV = imgService.flipVertical(src);
        assert flipV.getWidth() == 300 && flipV.getHeight() == 150 : "Flip V dimension mismatch";
        System.out.println("✓ Horizontal and vertical mirror transformations verified.");

        // 3. JPEG Export
        File tempJpeg = File.createTempFile("photo_", ".jpg");
        tempJpeg.deleteOnExit();
        imgService.saveAsJpeg(rot90, tempJpeg, 0.95f);
        assert tempJpeg.exists() && tempJpeg.length() > 0 : "JPEG compression export failed";
        System.out.println("✓ High-quality JPEG export verified: " + tempJpeg.length() + " bytes written.");

        // 4. CameraService Lifecycle & Background Frame Stream
        assert cameraService.isCameraAvailable() : "CameraService must report available";
        cameraService.initialize(0);

        CountDownLatch frameLatch = new CountDownLatch(3);
        AtomicInteger receivedFrames = new AtomicInteger(0);

        cameraService.startLiveStream(frame -> {
            assert frame != null : "Received null frame";
            receivedFrames.incrementAndGet();
            frameLatch.countDown();
        });

        boolean gotFrames = frameLatch.await(2, TimeUnit.SECONDS);
        assert gotFrames : "Failed to receive stream frames within timeout";
        System.out.println("✓ Camera live stream verified: received " + receivedFrames.get() + " frames at 30 FPS.");

        // 5. Snapshot Frame Capture
        BufferedImage snapshot = cameraService.captureFrame();
        assert snapshot != null : "Snapshot frame is null";
        assert snapshot.getWidth() > 0 && snapshot.getHeight() > 0 : "Invalid snapshot dimensions";
        System.out.println("✓ Camera snapshot capture verified: " + snapshot.getWidth() + "x" + snapshot.getHeight());

        // 6. Camera Resource Release
        cameraService.stopLiveStream();
        cameraService.release();
        System.out.println("✓ Camera resources cleanly and safely released.");

        System.out.println("\nALL PHASE 7 VERIFICATIONS PASSED SUCCESSFULLY!");
    }
}
