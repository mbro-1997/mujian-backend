package com.mujian.backend.scheme.service;

import com.mujian.backend.infrastructure.storage.BoundedMaterialReader;
import com.mujian.backend.scheme.domain.PdfSnapshot;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.springframework.stereotype.Service;

/** 固定scheme_v1模板：中文字体嵌入、卡片表格、显式分页和每页水印。 */
@Service
public class SchemePdfRenderer {
  private final BoundedMaterialReader boundedMaterialReader;
  private final Clock clock;

  /** 通过存储适配器读取可信快照图片，本类仅负责解码与PDF排版。 */
  public SchemePdfRenderer(BoundedMaterialReader boundedMaterialReader, Clock clock) {
    this.boundedMaterialReader = boundedMaterialReader;
    this.clock = clock;
  }

  /** 完整生成后才由文件服务原子发布，传输不在本方法内。 */
  public void render(PdfSnapshot pdfSnapshot, OutputStream outputStreamOutput, Instant deadline)
      throws IOException {
    try (PDDocument pDDocumentDocument = new PDDocument();
        InputStream inputStreamFontInput =
            getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf")) {
      if (inputStreamFontInput == null) throw new IOException("PDF font missing");
      PDType0Font pDType0FontFont =
          PDType0Font.load(pDDocumentDocument, inputStreamFontInput, true);
      List<PDPage> pDPageListPages = new ArrayList<>();
      PDPage pDPagePage = new PDPage(PDRectangle.A4);
      pDDocumentDocument.addPage(pDPagePage);
      pDPageListPages.add(pDPagePage);
      PDPageContentStream pDPageContentStreamStream =
          new PDPageContentStream(pDDocumentDocument, pDPagePage);
      float y = 790;
      java.math.BigInteger bigIntegerQuantity = java.math.BigInteger.ZERO;
      long daily = 0;
      Map<String, Long> styles = new LinkedHashMap<>();
      for (PdfSnapshot.Item item : pdfSnapshot.items()) {
        bigIntegerQuantity = bigIntegerQuantity.add(java.math.BigInteger.valueOf(item.quantity()));
        if (item.prices() != null)
          daily += (long) item.prices().dailyRentPriceFen() * item.quantity();
        for (String style : item.styleLabels())
          styles.merge(style, (long) item.quantity(), Long::sum);
      }
      String mainStyle = "暂无风格";
      long styleCount = 0;
      for (Map.Entry<String, Long> entryStyle : styles.entrySet())
        if (entryStyle.getValue() > styleCount) {
          mainStyle = entryStyle.getKey();
          styleCount = entryStyle.getValue();
        }
      text(pDPageContentStreamStream, pDType0FontFont, 16, 40, y, "MUHOU 幕间");
      pDPageContentStreamStream.setLineWidth(2);
      pDPageContentStreamStream.moveTo(40, y - 15);
      pDPageContentStreamStream.lineTo(555, y - 15);
      pDPageContentStreamStream.stroke();
      y -= 52;
      if (!pdfSnapshot.hidePrice()) {
        pDPageContentStreamStream.setNonStrokingColor(new java.awt.Color(255, 225, 0));
        pDPageContentStreamStream.addRect(440, y - 45, 115, 70);
        pDPageContentStreamStream.fill();
        pDPageContentStreamStream.setNonStrokingColor(0f);
        text(pDPageContentStreamStream, pDType0FontFont, 9, 450, y + 7, "预估日租");
        text(pDPageContentStreamStream, pDType0FontFont, 15, 450, y - 22, "¥" + money(daily));
      }
      for (String line :
          wrap(pDType0FontFont, pdfSnapshot.name(), 22, pdfSnapshot.hidePrice() ? 515 : 385)) {
        text(pDPageContentStreamStream, pDType0FontFont, 22, 40, y, line);
        y -= 28;
      }
      y -= 8;
      if (pdfSnapshot.description() != null)
        for (String line :
            wrap(
                pDType0FontFont,
                pdfSnapshot.description(),
                10,
                pdfSnapshot.hidePrice() ? 515 : 385)) {
          if (y < 115) {
            pDPageContentStreamStream.close();
            pDPagePage = new PDPage(PDRectangle.A4);
            pDDocumentDocument.addPage(pDPagePage);
            pDPageListPages.add(pDPagePage);
            pDPageContentStreamStream = new PDPageContentStream(pDDocumentDocument, pDPagePage);
            y = 790;
          }
          text(pDPageContentStreamStream, pDType0FontFont, 10, 40, y, line);
          y -= 16;
        }
      if (y < 215) {
        pDPageContentStreamStream.close();
        pDPagePage = new PDPage(PDRectangle.A4);
        pDDocumentDocument.addPage(pDPagePage);
        pDPageListPages.add(pDPagePage);
        pDPageContentStreamStream = new PDPageContentStream(pDDocumentDocument, pDPagePage);
        y = 790;
      }
      y -= 20;
      text(pDPageContentStreamStream, pDType0FontFont, 13, 40, y, "方案概览");
      y -= 15;
      String[] labels =
          pdfSnapshot.hidePrice()
              ? new String[] {"道具数量", "主要风格", "导出人"}
              : new String[] {"道具数量", "主要风格", "价格口径", "导出人"};
      String[] values =
          pdfSnapshot.hidePrice()
              ? new String[] {bigIntegerQuantity + " 件", mainStyle, pdfSnapshot.exportedBy()}
              : new String[] {
                bigIntegerQuantity + " 件", mainStyle, "展示价格", pdfSnapshot.exportedBy()
              };
      float width = (515 - 8 * (labels.length - 1)) / labels.length;
      int maxLines = 1;
      List<List<String>> valueLines = new ArrayList<>();
      for (String value : values) {
        List<String> lines =
            wrap(
                pDType0FontFont, value == null || value.isBlank() ? "幕间用户" : value, 10, width - 16);
        valueLines.add(lines);
        maxLines = Math.max(maxLines, lines.size());
      }
      float statHeight = 35 + maxLines * 14;
      for (int column = 0; column < labels.length; column++) {
        float left = 40 + column * (width + 8);
        pDPageContentStreamStream.setNonStrokingColor(new java.awt.Color(248, 250, 252));
        pDPageContentStreamStream.addRect(left, y - statHeight, width, statHeight);
        pDPageContentStreamStream.fill();
        pDPageContentStreamStream.setNonStrokingColor(0.5f);
        text(pDPageContentStreamStream, pDType0FontFont, 8, left + 8, y - 15, labels[column]);
        pDPageContentStreamStream.setNonStrokingColor(0f);
        float baseline = y - 32;
        for (String line : valueLines.get(column)) {
          text(pDPageContentStreamStream, pDType0FontFont, 10, left + 8, baseline, line);
          baseline -= 14;
        }
      }
      y -= statHeight + 29;
      text(pDPageContentStreamStream, pDType0FontFont, 13, 40, y, "道具清单");
      y -= 23;
      float[] x =
          pdfSnapshot.hidePrice()
              ? new float[] {40, 108, 260, 360, 395}
              : new float[] {40, 108, 245, 320, 350, 415};
      String[] headers =
          pdfSnapshot.hidePrice()
              ? new String[] {"图片", "道具名称", "分类 / 风格", "数量", "规格摘要"}
              : new String[] {"图片", "道具名称", "分类 / 风格", "数量", "日租", "规格摘要"};
      y = header(pDPageContentStreamStream, pDType0FontFont, x, headers, y);
      for (PdfSnapshot.Item item : pdfSnapshot.items()) {
        if (!clock.instant().isBefore(deadline)) throw new IOException("PDF generation deadline");
        String specs =
            item.sizeDesc()
                + " / "
                + item.materialDesc()
                + (item.remark() == null ? "" : "\n备注：" + item.remark());
        List<List<String>> cells = new ArrayList<>();
        cells.add(List.of());
        cells.add(
            wrap(pDType0FontFont, item.name() + "\n" + item.factoryName(), 9, x[2] - x[1] - 10));
        cells.add(
            wrap(
                pDType0FontFont,
                item.typeLabel() + "\n" + String.join(" / ", item.styleLabels()),
                8,
                x[3] - x[2] - 8));
        cells.add(wrap(pDType0FontFont, Integer.toString(item.quantity()), 9, 28));
        if (!pdfSnapshot.hidePrice())
          cells.add(wrap(pDType0FontFont, "¥" + money(item.prices().dailyRentPriceFen()), 9, 58));
        cells.add(wrap(pDType0FontFont, specs, 8, 555 - x[x.length - 1] - 6));
        int lines = 5;
        for (List<String> cell : cells) lines = Math.max(lines, cell.size());
        float height = lines * 13 + 18;
        if (y - height < 82) {
          pDPageContentStreamStream.close();
          pDPagePage = new PDPage(PDRectangle.A4);
          pDDocumentDocument.addPage(pDPagePage);
          pDPageListPages.add(pDPagePage);
          pDPageContentStreamStream = new PDPageContentStream(pDDocumentDocument, pDPagePage);
          text(pDPageContentStreamStream, pDType0FontFont, 16, 40, 790, "道具清单（续）");
          y = header(pDPageContentStreamStream, pDType0FontFont, x, headers, 760);
        }
        BufferedImage bufferedImageImage = productImage(item.imageKeys());
        if (bufferedImageImage != null) {
          PDImageXObject pDImageXObjectPdfImage =
              LosslessFactory.createFromImage(pDDocumentDocument, bufferedImageImage);
          float scale =
              Math.min(54f / bufferedImageImage.getWidth(), 54f / bufferedImageImage.getHeight());
          pDPageContentStreamStream.drawImage(
              pDImageXObjectPdfImage,
              40,
              y - 64,
              bufferedImageImage.getWidth() * scale,
              bufferedImageImage.getHeight() * scale);
        } else {
          pDPageContentStreamStream.setNonStrokingColor(0.95f);
          pDPageContentStreamStream.addRect(40, y - 64, 54, 54);
          pDPageContentStreamStream.fill();
          pDPageContentStreamStream.setNonStrokingColor(0f);
          text(pDPageContentStreamStream, pDType0FontFont, 8, 47, y - 38, "暂无图片");
        }
        for (int column = 1; column < cells.size(); column++) {
          float lineY = y - 15;
          for (String line : cells.get(column)) {
            text(
                pDPageContentStreamStream,
                pDType0FontFont,
                column == 2 || column == cells.size() - 1 ? 8 : 9,
                x[column],
                lineY,
                line);
            lineY -= 13;
          }
        }
        y -= height;
        pDPageContentStreamStream.setStrokingColor(0.86f);
        pDPageContentStreamStream.moveTo(40, y);
        pDPageContentStreamStream.lineTo(555, y);
        pDPageContentStreamStream.stroke();
        y -= 8;
      }
      if (pdfSnapshot.items().isEmpty())
        text(pDPageContentStreamStream, pDType0FontFont, 11, 40, y - 20, "本方案暂无道具");
      pDPageContentStreamStream.close();
      for (int index = 0; index < pDPageListPages.size(); index++)
        try (PDPageContentStream pDPageContentStreamFooter =
            new PDPageContentStream(
                pDDocumentDocument,
                pDPageListPages.get(index),
                PDPageContentStream.AppendMode.APPEND,
                true,
                true)) {
          try (PDPageContentStream pDPageContentStreamWatermark =
              new PDPageContentStream(
                  pDDocumentDocument,
                  pDPageListPages.get(index),
                  PDPageContentStream.AppendMode.PREPEND,
                  true,
                  false)) {
            pDPageContentStreamWatermark.saveGraphicsState();
            pDPageContentStreamWatermark.setNonStrokingColor(0.94f);
            for (float baseline : new float[] {200, 450, 680}) {
              pDPageContentStreamWatermark.saveGraphicsState();
              pDPageContentStreamWatermark.transform(
                  org.apache.pdfbox.util.Matrix.getRotateInstance(
                      Math.toRadians(28), 100, baseline));
              text(
                  pDPageContentStreamWatermark, pDType0FontFont, 23, 0, 0, pdfSnapshot.watermark());
              pDPageContentStreamWatermark.restoreGraphicsState();
            }
            pDPageContentStreamWatermark.restoreGraphicsState();
          }
          pDPageContentStreamFooter.setNonStrokingColor(0.35f);
          text(
              pDPageContentStreamFooter,
              pDType0FontFont,
              8,
              40,
              52,
              "本方案仅供参考，不锁定库存，不代表工厂最终可供或租赁承诺");
          text(pDPageContentStreamFooter, pDType0FontFont, 8, 40, 35, "幕间 MUHOU · 方案书仅供参考");
          text(
              pDPageContentStreamFooter,
              pDType0FontFont,
              8,
              465,
              35,
              "第 " + (index + 1) + " / " + pDPageListPages.size() + " 页");
        }
      pDDocumentDocument.save(outputStreamOutput);
    }
  }

  /** 表头与续页使用相同列宽。 */
  private float header(
      PDPageContentStream pDPageContentStreamStream,
      PDType0Font pDType0FontFont,
      float[] x,
      String[] headers,
      float y)
      throws IOException {
    pDPageContentStreamStream.setNonStrokingColor(new java.awt.Color(17, 24, 39));
    pDPageContentStreamStream.addRect(40, y - 24, 515, 28);
    pDPageContentStreamStream.fill();
    pDPageContentStreamStream.setNonStrokingColor(new java.awt.Color(255, 225, 0));
    for (int i = 0; i < x.length; i++)
      text(pDPageContentStreamStream, pDType0FontFont, 8, x[i] + 3, y - 14, headers[i]);
    pDPageContentStreamStream.setNonStrokingColor(0f);
    return y - 32;
  }

  /** 按字体实际宽度换行，保留用户文本，不执行HTML。 */
  private List<String> wrap(PDType0Font pDType0FontFont, String value, float size, float width)
      throws IOException {
    List<String> lines = new ArrayList<>();
    StringBuilder stringBuilderLine = new StringBuilder();
    for (int code : value.codePoints().toArray()) {
      if (code == '\n') {
        lines.add(stringBuilderLine.toString());
        stringBuilderLine.setLength(0);
        continue;
      }
      String glyph = new String(Character.toChars(code));
      try {
        pDType0FontFont.encode(glyph);
      } catch (IllegalArgumentException illegalArgumentExceptionException) {
        glyph = "□";
      }
      if (stringBuilderLine.length() > 0
          && pDType0FontFont.getStringWidth(stringBuilderLine + glyph) / 1000 * size > width) {
        lines.add(stringBuilderLine.toString());
        stringBuilderLine.setLength(0);
      }
      stringBuilderLine.append(glyph);
    }
    if (stringBuilderLine.length() > 0) lines.add(stringBuilderLine.toString());
    return lines;
  }

  /** 绘制已换行文本。 */
  private void text(
      PDPageContentStream pDPageContentStreamStream,
      PDType0Font pDType0FontFont,
      float size,
      float x,
      float y,
      String value)
      throws IOException {
    pDPageContentStreamStream.beginText();
    pDPageContentStreamStream.setFont(pDType0FontFont, size);
    pDPageContentStreamStream.newLineAtOffset(x, y);
    pDPageContentStreamStream.showText(value);
    pDPageContentStreamStream.endText();
  }

  /** 整数分格式化，禁止浮点价格。 */
  private String money(long value) {
    return java.math.BigDecimal.valueOf(value, 2).toPlainString();
  }

  /** 图片读取限时、限字节、限解码像素；缺图不丢失文字。 */
  private BufferedImage productImage(List<String> keys) {
    if (keys.isEmpty()) return null;
    try {
      byte[] bytes = boundedMaterialReader.readImage(keys.get(0));
      {
        try (ImageInputStream imageInputStreamImageInput =
            ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
          Iterator<ImageReader> iteratorReaders =
              ImageIO.getImageReaders(imageInputStreamImageInput);
          if (!iteratorReaders.hasNext()) return null;
          ImageReader imageReaderReader = iteratorReaders.next();
          try {
            imageReaderReader.setInput(imageInputStreamImageInput);
            if ((long) imageReaderReader.getWidth(0) * imageReaderReader.getHeight(0) > 20000000)
              return null;
            ImageReadParam imageReadParamParameters = imageReaderReader.getDefaultReadParam();
            int sample =
                Math.max(
                    1,
                    (Math.max(imageReaderReader.getWidth(0), imageReaderReader.getHeight(0)) + 1023)
                        / 1024);
            imageReadParamParameters.setSourceSubsampling(sample, sample, 0, 0);
            return imageReaderReader.read(0, imageReadParamParameters);
          } finally {
            imageReaderReader.dispose();
          }
        }
      }
    } catch (Exception exception) {
      if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
      return null;
    }
  }
}
