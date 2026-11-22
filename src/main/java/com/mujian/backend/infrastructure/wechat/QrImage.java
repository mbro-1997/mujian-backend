package com.mujian.backend.infrastructure.wechat;

import com.mujian.backend.common.enums.BusinessFailureReason;
import static com.mujian.backend.common.support.D2Values.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Decode image bytes before storage; JSON with a misleading content type never becomes ready. */
public final class QrImage {
  private QrImage() {}

  /** 将二维码内容编码为 PNG。 */
  public static byte[] png(byte[] data) {
    try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name());
      ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w < 1 || h < 1 || (long) w * h > 20_000_000)
          throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name());
        BufferedImage image = reader.read(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name());
        return out.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (Exception e) {
      throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name(), e);
    }
  }
}
