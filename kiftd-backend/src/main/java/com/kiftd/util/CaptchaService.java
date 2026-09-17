package com.kiftd.util;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CaptchaService {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public record CaptchaResult(String captchaId, byte[] imagePng) {}

    public CaptchaResult create() {
        String code = String.format("%04d", random.nextInt(10000));
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, code);
        return new CaptchaResult(id, render(code));
    }

    public boolean verify(String captchaId, String code) {
        if (captchaId == null || code == null) {
            return false;
        }
        String expect = store.remove(captchaId);
        return expect != null && expect.equalsIgnoreCase(code.trim());
    }

    private byte[] render(String code) {
        int w = 100, h = 36;
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(240, 240, 240));
        g.fillRect(0, 0, w, h);
        g.setFont(new Font("Arial", Font.BOLD, 22));
        g.setColor(new Color(40, 40, 40));
        g.drawString(code, 22, 26);
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            g.drawLine(random.nextInt(w), random.nextInt(h), random.nextInt(w), random.nextInt(h));
        }
        g.dispose();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
