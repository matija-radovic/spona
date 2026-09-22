package io.github.matijaradovic.spona.storage;

import static io.github.matijaradovic.spona.support.AvatarFixtures.canvasPng;
import static io.github.matijaradovic.spona.support.AvatarFixtures.dataUrl;
import static io.github.matijaradovic.spona.support.AvatarFixtures.image;
import static io.github.matijaradovic.spona.support.AvatarFixtures.pngDataUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.entry;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.matijaradovic.spona.common.error.ErrorCode;
import io.github.matijaradovic.spona.common.error.UnprocessableException;

class ImageValidatorTest {

    private static final int HALF_A_MEGABYTE = 512 * 1024;

    @Test
    void acceptsWhatABrowserCanvasExports() {
        byte[] png = canvasPng();

        assertThat(ImageValidator.avatar(pngDataUrl(png))).isEqualTo(png);
    }

    @ParameterizedTest
    @ValueSource(ints = { BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_BYTE_GRAY,
            BufferedImage.TYPE_BYTE_INDEXED })
    void acceptsA256SquarePngInAnyColourType(int type) {
        byte[] png = image("png", 256, 256, type);

        assertThat(ImageValidator.avatar(pngDataUrl(png))).isEqualTo(png);
    }

    @ParameterizedTest
    @CsvSource({ "255, 256", "256, 255", "300, 200", "512, 512" })
    void refusesAPngOfAnyOtherSize(int width, int height) {
        assertRefused(pngDataUrl(image("png", width, height)), "The avatar must be exactly 256 by 256 pixels.");
    }

    @ParameterizedTest
    @ValueSource(strings = { "jpg", "bmp" })
    void refusesAnotherFormatEvenWhenLabelledPng(String format) {
        assertRefused(pngDataUrl(image(format, 256, 256)), "The avatar is not a PNG.");
    }

    @Test
    void refusesADataUrlOfAnyOtherType() {
        assertRefused(dataUrl("image/webp", canvasPng()), "The avatar must be a PNG data URL.");
    }

    @Test
    void refusesBase64WithNoDataUrlPrefix() {
        assertRefused(Base64.getEncoder().encodeToString(canvasPng()), "The avatar must be a PNG data URL.");
    }

    @Test
    void refusesMalformedBase64AsUnprocessableRatherThanThrowingIt() {
        assertRefused("data:image/png;base64,iVBOR*not base64*", "The avatar is not valid base64.");
    }

    @Test
    void acceptsAPngOfExactlyHalfAMegabyte() {
        byte[] png = Arrays.copyOf(canvasPng(), HALF_A_MEGABYTE);

        assertThat(ImageValidator.avatar(pngDataUrl(png))).hasSize(HALF_A_MEGABYTE);
    }

    @Test
    void refusesAPngOneByteOverHalfAMegabyte() {
        assertRefused(pngDataUrl(Arrays.copyOf(canvasPng(), HALF_A_MEGABYTE + 1)), "At most 512 KB.");
    }

    @Test
    void refusesAnOverlongDataUrlBeforeDecodingIt() {
        assertRefused("data:image/png;base64," + "!".repeat(HALF_A_MEGABYTE * 2), "At most 512 KB.");
    }

    @Test
    void refusesAFileTooShortToHoldAHeader() {
        assertRefused(pngDataUrl(Arrays.copyOf(canvasPng(), 20)), "The avatar is not a PNG.");
    }

    @Test
    void refusesAPngWhoseFirstChunkIsNotItsHeader() {
        byte[] png = canvasPng();
        png[12] = 'X';

        assertRefused(pngDataUrl(png), "The avatar is not a PNG.");
    }

    @Test
    void refusesAPngWhoseImageDataIsCutShort() {
        assertRefused(pngDataUrl(Arrays.copyOf(canvasPng(), 200)), "The avatar is not a valid PNG.");
    }

    @Test
    void refusesAPngWhoseImageDataIsGarbage() {
        byte[] png = canvasPng();
        Arrays.fill(png, 41, 120, (byte) 0xff);

        assertRefused(pngDataUrl(png), "The avatar is not a valid PNG.");
    }

    @Test
    void theSizeRefusalsCarryTheirBoundsAsArguments() {
        assertThat(refusal(pngDataUrl(image("png", 300, 200))))
                .satisfies(error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.AVATAR_WRONG_DIMENSIONS))
                .satisfies(error -> assertThat(error.args()).containsExactly(entry("width", 256), entry("height", 256)));
        assertThat(refusal(pngDataUrl(Arrays.copyOf(canvasPng(), HALF_A_MEGABYTE + 1))))
                .satisfies(error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.AVATAR_TOO_LARGE))
                .satisfies(error -> assertThat(error.args()).containsExactly(entry("max", 512)));
    }

    private static void assertRefused(String dataUrl, String message) {
        assertThat(refusal(dataUrl).detail()).isEqualTo(message);
    }

    private static ErrorCode.Formatted refusal(String dataUrl) {
        UnprocessableException refusal = catchThrowableOfType(UnprocessableException.class,
                () -> ImageValidator.avatar(dataUrl));
        assertThat(refusal.getErrors()).containsOnlyKeys("avatar");
        return refusal.getErrors().get("avatar");
    }
}
