package com.clinicdesk.camera;

import com.clinicdesk.logging.AppLogger;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service providing rotation, flipping, format conversions,
 * and high-quality JPEG compression for captured clinical photographs.
 */
public class ImageProcessingService {

    private static final Logger LOGGER = AppLogger.getLogger(ImageProcessingService.class);

    /**
     * Rotates an image clockwise by 90, 180, or 270 degrees.
     */
    public BufferedImage rotate(BufferedImage src, int degrees) {
        if (src == null) {
            return null;
        }

        int normalized = ((degrees % 360) + 360) % 360;
        if (normalized == 0) {
            return src;
        }

        int srcW = src.getWidth();
        int srcH = src.getHeight();

        int targetW = (normalized == 90 || normalized == 270) ? srcH : srcW;
        int targetH = (normalized == 90 || normalized == 270) ? srcW : srcH;

        BufferedImage dest = new BufferedImage(targetW, targetH, src.getType() != 0 ? src.getType() : BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dest.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            AffineTransform tx = new AffineTransform();
            switch (normalized) {
                case 90 -> {
                    tx.translate(targetW, 0);
                    tx.rotate(Math.toRadians(90));
                }
                case 180 -> {
                    tx.translate(targetW, targetH);
                    tx.rotate(Math.toRadians(180));
                }
                case 270 -> {
                    tx.translate(0, targetH);
                    tx.rotate(Math.toRadians(270));
                }
            }

            g2d.drawImage(src, tx, null);
        } finally {
            g2d.dispose();
        }

        return dest;
    }

    /**
     * Flips an image horizontally (mirror across vertical axis).
     */
    public BufferedImage flipHorizontal(BufferedImage src) {
        if (src == null) {
            return null;
        }

        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, src.getType() != 0 ? src.getType() : BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dest.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
            tx.translate(-w, 0);
            g2d.drawImage(src, tx, null);
        } finally {
            g2d.dispose();
        }

        return dest;
    }

    /**
     * Flips an image vertically (mirror across horizontal axis).
     */
    public BufferedImage flipVertical(BufferedImage src) {
        if (src == null) {
            return null;
        }

        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dest = new BufferedImage(w, h, src.getType() != 0 ? src.getType() : BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dest.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
            tx.translate(0, -h);
            g2d.drawImage(src, tx, null);
        } finally {
            g2d.dispose();
        }

        return dest;
    }

    /**
     * Saves a BufferedImage to a local temporary or permanent JPEG file with specified compression quality.
     */
    public void saveAsJpeg(BufferedImage img, File outputFile, float quality) throws IOException {
        if (img == null || outputFile == null) {
            throw new IllegalArgumentException("Image and output file cannot be null.");
        }

        // Convert to RGB if ARGB to prevent JPEG color distortions
        BufferedImage rgbImage = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            rgbImage = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgbImage.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG image writer found on system.");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.clamp(quality, 0.1f, 1.0f));
        }

        try (FileImageOutputStream output = new FileImageOutputStream(outputFile)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(rgbImage, null, null), param);
        } finally {
            writer.dispose();
        }

        LOGGER.info("Exported JPEG image: " + outputFile.getAbsolutePath() + " (" + outputFile.length() + " bytes)");
    }
}
