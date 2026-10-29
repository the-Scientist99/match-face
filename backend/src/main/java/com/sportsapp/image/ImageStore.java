package com.sportsapp.image;

import com.sportsapp.common.ApiException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Photos and logos on local disk, one {@code <uuid>.jpg} per upload. Nothing of the upload is kept: it is
 * decoded, cropped to a square, scaled down to at most {@link #SIZE} px and written out as a fresh JPEG, so
 * metadata (EXIF, GPS) and anything hidden in the original are dropped.
 */
@Component
public class ImageStore {

  static final int SIZE = 256;
  /** Checked before decoding, so a tiny file claiming a huge image can't exhaust memory. */
  private static final int MAX_SIDE = 8000;
  private static final Set<String> FORMATS = Set.of("jpeg", "png");
  private static final Pattern NAME = Pattern.compile("[0-9a-f-]{36}\\.jpg");
  private static final Logger log = LoggerFactory.getLogger(ImageStore.class);

  private final Path dir;

  public ImageStore(@Value("${app.images.dir}") Path dir) throws IOException {
    this.dir = Files.createDirectories(dir.toAbsolutePath());
    ImageIO.setUseCache(false); // decode in memory, no temp files
  }

  public Path dir() { return dir; }

  /** {@code /api/images/<file>}, or null for no image. */
  public static String url(@Nullable String file) {
    return file == null ? null : "/api/images/" + file;
  }

  /** Stores a JPEG or PNG and returns its file name; anything else is {@code INVALID_IMAGE}. */
  public String save(InputStream in) {
    BufferedImage square = toSquare(decode(in));
    String file = UUID.randomUUID() + ".jpg";
    Path tmp = dir.resolve(file + ".tmp");
    try {
      writeJpeg(square, tmp);
      Files.move(tmp, dir.resolve(file), StandardCopyOption.ATOMIC_MOVE);
    } catch (IOException e) {
      try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* best effort */ }
      throw new UncheckedIOException(e);
    }
    return file;
  }

  /**
   * Deletes a stored image once the surrounding transaction commits (right away outside one), so a rollback
   * never leaves a row pointing at a missing file. Names that aren't ours are ignored.
   */
  public void deleteAfterCommit(@Nullable String file) {
    if (file == null || !NAME.matcher(file).matches()) return;
    Runnable delete = () -> {
      try {
        Files.deleteIfExists(dir.resolve(file));
      } catch (IOException e) {
        log.warn("Could not delete image {}", file, e);
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override public void afterCommit() { delete.run(); }
      });
    } else {
      delete.run();
    }
  }

  private static BufferedImage decode(InputStream in) {
    try (ImageInputStream stream = ImageIO.createImageInputStream(in)) {
      Iterator<ImageReader> readers = stream == null ? null : ImageIO.getImageReaders(stream);
      if (readers == null || !readers.hasNext()) throw invalid();
      ImageReader reader = readers.next();
      try {
        if (!FORMATS.contains(reader.getFormatName().toLowerCase())) throw invalid();
        reader.setInput(stream, true, true);
        if (reader.getWidth(0) > MAX_SIDE || reader.getHeight(0) > MAX_SIDE) throw invalid();
        return reader.read(0);
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException e) {
      if (e instanceof ApiException a) throw a;
      throw invalid(); // truncated or unsupported data (e.g. a CMYK JPEG)
    }
  }

  /** Center crop, scaled down only, flattened onto white (PNG transparency would otherwise turn black). */
  private static BufferedImage toSquare(BufferedImage src) {
    int side = Math.min(src.getWidth(), src.getHeight());
    int sx = (src.getWidth() - side) / 2;
    int sy = (src.getHeight() - side) / 2;
    int size = Math.min(side, SIZE);
    // ponytail: one bicubic step; aliasing shows only on big downscales, and browsers send 512 px already
    BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, size, size);
      g.drawImage(src, 0, 0, size, size, sx, sy, sx + side, sy + side, null);
    } finally {
      g.dispose();
    }
    return out;
  }

  private static void writeJpeg(BufferedImage img, Path to) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (ImageOutputStream out = ImageIO.createImageOutputStream(to.toFile())) {
      writer.setOutput(out);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(0.85f);
      writer.write(null, new IIOImage(img, null, null), param);
    } finally {
      writer.dispose();
    }
  }

  private static ApiException invalid() {
    return ApiException.badRequest("INVALID_IMAGE");
  }
}
