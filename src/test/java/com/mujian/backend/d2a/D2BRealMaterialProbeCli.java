package com.mujian.backend.d2a;

import com.mujian.backend.material.service.MaterialCleanupAudit;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.storage.CosMaterialStore;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.Stored;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.core.env.*;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** B授权前缀中仅上传一张生成的2x2测试图片；不执行删除、不修改任何数据库或桶策略。 */
public final class D2BRealMaterialProbeCli {
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    ObjectMapper json = new ObjectMapper();
    Path state = Path.of(".local/test/d2b-real-material.json");
    LinkedHashMap<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("time", Instant.now().toString());
    report.put("prefix", CosMaterialStore.FACTORY_PREFIX);
    report.put("realChannel", true);
    report.put("deleteExecuted", false);
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.qcloud.cos"))
        .setLevel(ch.qos.logback.classic.Level.OFF);
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.http"))
        .setLevel(ch.qos.logback.classic.Level.OFF);
    if (args.length == 1 && args[0].equals("expiry")) {
      JsonNode s = json.readTree(Files.readString(state));
      if (Instant.now().isBefore(Instant.parse(s.path("expiresAt").asString()).plusSeconds(5)))
        throw new IllegalStateException("签名尚未实际到期，不能标通过");
      report.put("expiredSignatureHttp", D2RealChannelProbeCli.get(s.path("signedUrl").asString()));
    } else {
      if (Files.exists(state)) throw new IllegalStateException("已有真实样本，禁止覆盖或重复上传");
      Path config = Path.of(".local/runtime.properties");
      if (!Files.getPosixFilePermissions(config)
          .equals(PosixFilePermissions.fromString("rw-------")))
        throw new IllegalStateException("凭据文件必须600");
      Properties p = new Properties();
      try (InputStream in = Files.newInputStream(config)) {
        p.load(in);
      }
      StandardEnvironment env = new StandardEnvironment();
      env.getPropertySources().addFirst(new PropertiesPropertySource("local", p));
      CosMaterialStore store = new CosMaterialStore(env);
      try {
        String owner = Ids.next(),
            upload = Ids.next(),
            key = CosMaterialStore.FACTORY_PREFIX + owner + "/" + upload;
        Instant now = Instant.now(), expires = now.plusSeconds(120);
        Map<String, String> metadata =
            Map.of(
                "owner",
                owner,
                "upload-id",
                upload,
                "purpose",
                "factory_avatar",
                "consent-version",
                "factory-material-v1",
                "accepted-at",
                now.toString(),
                "accept-until",
                now.plus(Duration.ofHours(24)).toString());
        store.put(key, D2ApplicationTest.png(), "image/png", metadata);
        Stored head = store.head(key);
        String signed = store.sign(key, expires);
        Files.createFile(
            state,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(
            state,
            json.writeValueAsString(
                Map.of(
                    "key",
                    key,
                    "signedUrl",
                    signed,
                    "expiresAt",
                    expires.toString(),
                    "eligibleAt",
                    head.lastModified().plus(Duration.ofHours(48)).toString())));
        report.put("objectKeyHash", MaterialCleanupAudit.digest(key));
        report.put("metadataMatches", head.metadata().entrySet().containsAll(metadata.entrySet()));
        report.put("signedHttp", D2RealChannelProbeCli.get(signed));
        report.put(
            "anonymousHttp",
            D2RealChannelProbeCli.get(
                "https://cct1-1429809468.cos.ap-shanghai.myqcloud.com/" + key));
        report.put(
            "factoryListContainsSample",
            store.list("B:").objects().stream().anyMatch(o -> o.key().equals(key)));
        report.put("deleteGate", store.deletionPolicyVerified());
        report.put("expiresAt", expires.toString());
      } finally {
        store.close();
      }
    }
    System.out.println(json.writeValueAsString(report));
  }
}
