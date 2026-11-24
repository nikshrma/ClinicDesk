package com.clinicdesk.ui.util;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

import java.awt.image.BufferedImage;

/**
 * Utility for converting standard BufferedImage objects to JavaFX Images.
 */
public final class FxImageUtils {

    private FxImageUtils() {
        // Utility
    }

    public static Image toFXImage(BufferedImage bimg) {
        if (bimg == null) {
            return null;
        }

        int width = bimg.getWidth();
        int height = bimg.getHeight();
        WritableImage writableImage = new WritableImage(width, height);
        PixelWriter pw = writableImage.getPixelWriter();

        int[] buffer = new int[width];
        for (int y = 0; y < height; y++) {
            bimg.getRGB(0, y, width, 1, buffer, 0, width);
            for (int x = 0; x < width; x++) {
                pw.setArgb(x, y, buffer[x]);
            }
        }

        return writableImage;
    }
}
