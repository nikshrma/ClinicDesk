package com.clinicdesk.camera;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageProcessingServiceTest {

    private ImageProcessingService service;

    @BeforeEach
    void setup() {
        service = new ImageProcessingService();
    }

    @Test
    void testRotate90Degrees() {
        // Create 200x100 rectangular image
        BufferedImage src = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = src.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 50); // Mark top-left quadrant
        g.dispose();

        BufferedImage rotated90 = service.rotate(src, 90);
        assertNotNull(rotated90);
        assertEquals(100, rotated90.getWidth(), "Width and height must swap after 90 degree rotation");
        assertEquals(200, rotated90.getHeight());

        BufferedImage rotated180 = service.rotate(src, 180);
        assertEquals(200, rotated180.getWidth());
        assertEquals(100, rotated180.getHeight());

        BufferedImage rotated270 = service.rotate(src, 270);
        assertEquals(100, rotated270.getWidth());
        assertEquals(200, rotated270.getHeight());
    }

    @Test
    void testFlipHorizontalAndVertical() {
        BufferedImage src = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        src.setRGB(10, 20, Color.BLUE.getRGB());

        // Horizontal flip mirrors X coordinate: (10, 20) -> (100 - 1 - 10, 20) = (89, 20)
        BufferedImage flippedH = service.flipHorizontal(src);
        assertEquals(100, flippedH.getWidth());
        assertEquals(100, flippedH.getHeight());
        assertEquals(Color.BLUE.getRGB(), flippedH.getRGB(89, 20), "Pixel at X=10 should be mirrored to X=89");

        // Vertical flip mirrors Y coordinate: (10, 20) -> (10, 100 - 1 - 20) = (10, 79)
        BufferedImage flippedV = service.flipVertical(src);
        assertEquals(100, flippedV.getWidth());
        assertEquals(100, flippedV.getHeight());
        assertEquals(Color.BLUE.getRGB(), flippedV.getRGB(10, 79), "Pixel at Y=20 should be mirrored to Y=79");
    }

    @Test
    void testSaveAsJpeg(@TempDir Path tempDir) throws IOException {
        BufferedImage img = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 300, 200);
        g.setColor(Color.BLACK);
        g.drawString("ClinicDesk Prescription Test", 50, 100);
        g.dispose();

        File target = tempDir.resolve("test_prescription.jpg").toFile();
        service.saveAsJpeg(img, target, 0.90f);

        assertTrue(target.exists(), "Target JPEG file must be written to disk");
        assertTrue(target.length() > 0, "Target file size must be greater than zero");
    }
}
