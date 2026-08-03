package com.aaron.cloud.common.document;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class LocalTesseractImageOcrPrepareTest {

    @Test
    void prepareForOcr_upscalesSmallImage() {
        BufferedImage small = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        BufferedImage out = LocalTesseractImageOcr.prepareForOcr(small);
        assertTrue(Math.min(out.getWidth(), out.getHeight()) >= 1800);
    }
}
