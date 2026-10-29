package com.sportsapp.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sportsapp.common.ApiException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageStoreTest {

  @TempDir Path dir;

  private ImageStore store() throws IOException {
    return new ImageStore(dir);
  }

  private static byte[] encode(BufferedImage img, String format) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, format, out);
    return out.toByteArray();
  }

  private BufferedImage saved(String file) throws IOException {
    assertThat(file).matches("[0-9a-f-]{36}\\.jpg");
    return ImageIO.read(dir.resolve(file).toFile());
  }

  @Test
  void pngWithAlphaBecomes256SquareJpeg() throws IOException {
    String file = store().save(new ByteArrayInputStream(encode(new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB), "png")));
    BufferedImage out = saved(file);
    assertThat(out.getWidth()).isEqualTo(256);
    assertThat(out.getHeight()).isEqualTo(256);
    assertThat(out.getRGB(10, 10) & 0xFFFFFF).isGreaterThan(0xF0F0F0); // transparent → white
    try (var files = Files.list(dir)) {
      assertThat(files.map(p -> p.getFileName().toString())).containsExactly(file);
    }
  }

  @Test
  void smallImageIsNotUpscaled() throws IOException {
    BufferedImage out = saved(store().save(new ByteArrayInputStream(encode(new BufferedImage(100, 80, BufferedImage.TYPE_INT_RGB), "jpg"))));
    assertThat(out.getWidth()).isEqualTo(80);
    assertThat(out.getHeight()).isEqualTo(80);
  }

  @Test
  void rejectsNonImagesAndOtherFormats() throws IOException {
    byte[] noise = new byte[2000];
    new Random(1).nextBytes(noise);
    ImageStore store = store();
    assertThatThrownBy(() -> store.save(new ByteArrayInputStream(noise)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_IMAGE"));
    byte[] gif = encode(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "gif");
    assertThatThrownBy(() -> store.save(new ByteArrayInputStream(gif)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_IMAGE"));
  }

  @Test
  void rejectsHugeDimensions() throws IOException {
    byte[] png = encode(new BufferedImage(9000, 1, BufferedImage.TYPE_BYTE_GRAY), "png");
    ImageStore store = store();
    assertThatThrownBy(() -> store.save(new ByteArrayInputStream(png)))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_IMAGE"));
  }

  @Test
  void deleteRemovesOnlyStoredImages() throws IOException {
    ImageStore store = store();
    String file = store.save(new ByteArrayInputStream(encode(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), "png")));
    Path sibling = Files.writeString(dir.resolveSibling("x"), "keep");
    store.deleteAfterCommit("../x");
    store.deleteAfterCommit(null);
    assertThat(sibling).exists();
    store.deleteAfterCommit(file);
    assertThat(dir.resolve(file)).doesNotExist();
  }

  @Test
  void url() {
    assertThat(ImageStore.url(null)).isNull();
    assertThat(ImageStore.url("a.jpg")).isEqualTo("/api/images/a.jpg");
  }
}
