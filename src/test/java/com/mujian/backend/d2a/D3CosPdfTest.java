package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.infrastructure.storage.BoundedMaterialReader;
import com.mujian.backend.infrastructure.storage.CosMaterialStore;
import com.mujian.backend.planning.vo.PlanningViews.Prices;
import com.mujian.backend.scheme.domain.PdfSnapshot;
import com.mujian.backend.scheme.service.SchemePdfRenderer;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.*;
import tools.jackson.databind.*;

/** 只读取既有虚构COS样本；不启动业务库、不上传、不删除。 */
class D3CosPdfTest {
  /** 验证真实签名图片可嵌入含价与隐价 PDF。 */
  @Test
  void realCosSignedImageIsEmbeddedInPricedAndHiddenPdf() throws Exception {
    Path runtime = Path.of(".local/runtime.properties");
    assertEquals(
        PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(runtime));
    Properties secrets = new Properties();
    try (InputStream input = Files.newInputStream(runtime)) {
      secrets.load(input);
    }
    Properties cosOnly = new Properties();
    for (String name : List.of("mj.cos.secret-id", "mj.cos.secret-key")) {
      assertNotNull(secrets.getProperty(name), "existing COS configuration required");
      cosOnly.setProperty(name, secrets.getProperty(name));
    }
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new PropertiesPropertySource("cos-only", cosOnly));
    ObjectMapper json = new ObjectMapper();
    JsonNode samples =
        json.readTree(Files.readString(Path.of(".local/test/remaining-cloud-samples.json")));
    String key = samples.path("samples").get(0).path("key").asString();
    assertTrue(
        key.startsWith(CosMaterialStore.PREFIX + "student/"),
        "reuse exact registered fictional sample");
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.qcloud.cos"))
        .setLevel(ch.qos.logback.classic.Level.OFF);
    CosMaterialStore cosMaterialStore = new CosMaterialStore(environment);
    Path evidence = Path.of("../../开发交接/D3-最小首次交易/证据/D3-A");
    try {
      long started = System.nanoTime();
      try {
        java.net.URI uri =
            java.net.URI.create(cosMaterialStore.sign(key, Instant.now().plusSeconds(60)));
        java.net.http.HttpResponse<byte[]> response =
            java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build()
                .send(
                    java.net.http.HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(12))
                        .GET()
                        .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
        System.out.println(
            "COS signed read status="
                + response.statusCode()
                + " bytes="
                + response.body().length
                + " elapsedMs="
                + (System.nanoTime() - started) / 1000000);
        assertEquals(200, response.statusCode());
      } catch (Exception failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause())
          System.out.println("COS signed read failureType=" + cause.getClass().getName());
        throw new IllegalStateException("COS read failed; no raw signed URL logged");
      }
      SchemePdfRenderer schemePdfRenderer =
          new SchemePdfRenderer(
              new BoundedMaterialReader(cosMaterialStore, Clock.systemUTC()), Clock.systemUTC());
      for (boolean hidden : List.of(false, true)) {
        PdfSnapshot snapshot =
            new PdfSnapshot(
                "真实图片方案",
                "既有虚构测试图；只读验证",
                1,
                "scheme_v1",
                hidden,
                "MUHOU 幕间",
                Instant.now().toString(),
                "隔离验证",
                List.of(
                    new PdfSnapshot.Item(
                        "测试图片道具",
                        "隔离工厂",
                        "道具",
                        List.of("现代"),
                        "测试规格",
                        "测试材质",
                        List.of(key),
                        1,
                        "签名读取与图片嵌入",
                        hidden ? null : new Prices(12345, 20000))));
        Path file = evidence.resolve(hidden ? "scheme-cos-hidden.pdf" : "scheme-cos-priced.pdf");
        try (OutputStream output = Files.newOutputStream(file)) {
          schemePdfRenderer.render(snapshot, output, Instant.now().plusSeconds(120));
        }
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
          int images = 0;
          for (org.apache.pdfbox.pdmodel.PDPage page : document.getPages())
            for (org.apache.pdfbox.cos.COSName name : page.getResources().getXObjectNames())
              if (page.getResources().getXObject(name) instanceof PDImageXObject image) {
                assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
                ImageIO.write(
                    image.getImage(), "png", evidence.resolve("cos-pdf-extracted.png").toFile());
                images++;
              }
          assertEquals(1, images, "COS image must be a real PDF image object, never a placeholder");
          String text = new PDFTextStripper().getText(document);
          assertTrue(text.contains("真实图片方案"));
          assertEquals(!hidden, text.contains("123.45"));
          if (hidden) {
            assertFalse(text.contains("预估日租"));
            assertFalse(text.contains("¥"));
          }
        }
      }
    } finally {
      cosMaterialStore.close();
    }
  }
}
