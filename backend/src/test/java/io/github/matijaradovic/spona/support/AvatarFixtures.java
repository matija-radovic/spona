package io.github.matijaradovic.spona.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Base64;

import javax.imageio.ImageIO;

public final class AvatarFixtures {

    private AvatarFixtures() {
    }

    public static byte[] canvasPng() {
        try (InputStream in = AvatarFixtures.class.getResourceAsStream("/avatars/red-256x256.png")) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static byte[] image(String format, int width, int height) {
        return image(format, width, height, BufferedImage.TYPE_INT_RGB);
    }

    public static byte[] image(String format, int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("No " + format + " writer for image type " + type);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    public static String dataUrl(String type, byte[] bytes) {
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    public static String pngDataUrl(byte[] bytes) {
        return dataUrl("image/png", bytes);
    }
}
