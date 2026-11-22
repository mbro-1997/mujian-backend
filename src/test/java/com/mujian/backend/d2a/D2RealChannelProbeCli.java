package com.mujian.backend.d2a;

import com.mujian.backend.material.service.MaterialCleanupAudit;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.storage.CosMaterialStore;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.Stored;
import com.qcloud.cos.*;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.*;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.BucketLifecycleConfiguration;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.region.Region;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.core.env.*;
import org.springframework.core.env.StandardEnvironment;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Explicitly invoked real-channel probe. Only fictional images under the approved D2 prefix. No DB.
 */
public class D2RealChannelProbeCli {
  static final String BUCKET = "cct1-1429809468";
  static final ObjectMapper JSON = new ObjectMapper();
  static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(4))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  interface Query {
    /** 执行本组用例指定的操作。 */
    Object run() throws Exception;
  }

  /** 核对输入或业务返回值。 */
  static Object checked(Query d2RealChannelProbeCliQuery) {
    try {
      return d2RealChannelProbeCliQuery.run();
    } catch (CosServiceException e) {
      return Map.of(
          "status", e.getStatusCode(), "code", Objects.toString(e.getErrorCode(), "unknown"));
    } catch (Exception e) {
      return Map.of("errorType", e.getClass().getSimpleName());
    }
  }

  /** 读取指定测试资源。 */
  static int get(String url) throws Exception {
    return HTTP.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
            HttpResponse.BodyHandlers.discarding())
        .statusCode();
  }

  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("com.qcloud.cos"))
        .setLevel(ch.qos.logback.classic.Level.OFF);
    ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("org.apache.http"))
        .setLevel(ch.qos.logback.classic.Level.OFF);
    if (args.length != 1 || !Set.of("inspect", "upload", "expiry", "retention", "age48").contains(args[0]))
      throw new IllegalArgumentException("inspect|upload|expiry|retention|age48");
    Path config = Path.of(".local/runtime.properties").toAbsolutePath();
    if (!Files.getPosixFilePermissions(config).equals(PosixFilePermissions.fromString("rw-------")))
      throw new IllegalStateException("configuration must be 600");
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(config)) {
      props.load(in);
    }
    ClientConfig cfg = new ClientConfig(new Region("ap-shanghai"));
    cfg.setHttpProtocol(HttpProtocol.https);
    cfg.setMaxErrorRetry(0);
    cfg.setConnectionTimeout(4000);
    cfg.setSocketTimeout(15000);
    COSClient client =
        new COSClient(
            new BasicCOSCredentials(
                props.getProperty("mj.cos.secret-id"), props.getProperty("mj.cos.secret-key")),
            cfg);
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().addFirst(new PropertiesPropertySource("protected-runtime", props));
    CosMaterialStore store = new CosMaterialStore(env);
    LinkedHashMap<String, Object> report = new LinkedHashMap<String, Object>();
    report.put("mode", args[0]);
    report.put("time", Instant.now().toString());
    report.put("bucket", BUCKET);
    report.put("prefix", CosMaterialStore.PREFIX);
    Path statePath = Path.of(".local/test/real-channel-state.json");
    try {
      if (args[0].equals("age48")) {
        JsonNode state = JSON.readTree(Files.readString(statePath));
        String key = state.get("key").asString();
        String digest = MaterialCleanupAudit.digest(key);
        if (!digest.equals("c2f22873b2ab20f14055d51851b8bc519698693bd91ce5fc21b1c0cc9033758d"))
          throw new IllegalStateException("original approved test object only");
        Stored object = store.head(key);
        Map<String, String> m = object.metadata();
        report.put("objectKeyHash", digest);
        report.put("createdAt", object.lastModified().toString());
        report.put("originalAgeUnchanged", object.lastModified().toString().equals(state.get("createdAt").asString()));
        report.put("actualAgeOver48h", !Instant.now().isBefore(object.lastModified().plus(Duration.ofHours(48))));
        report.put("metadataMatchesTestScope", key.equals(CosMaterialStore.PREFIX + "student/" + m.get("owner") + "/" + m.get("upload-id"))
            && "student_card".equals(m.get("purpose")) && "student-material-v1".equals(m.get("consent-version")));
        report.put("version", checked(() -> client.getBucketVersioningConfiguration(BUCKET).getStatus()));
        report.put("lifecycle", checked(() -> {
          BucketLifecycleConfiguration c = client.getBucketLifecycleConfiguration(BUCKET);
          return c == null || c.getRules() == null ? List.of() : c.getRules().stream().map(r -> Map.of("status", r.getStatus())).toList();
        }));
        report.put("deletionPolicyVerified", store.deletionPolicyVerified());
        report.put("deleted", false);
        report.put("boundary", "read-only exact original test object; no owner or reference bypass");
      } else if (args[0].equals("inspect")) {
        report.put(
            "version", checked(() -> client.getBucketVersioningConfiguration(BUCKET).getStatus()));
        report.put(
            "lifecycle",
            checked(
                () -> {
                  BucketLifecycleConfiguration c = client.getBucketLifecycleConfiguration(BUCKET);
                  return c == null
                      ? List.of()
                      : c.getRules().stream().map(r -> Map.of("status", r.getStatus())).toList();
                }));
        report.put(
            "acl",
            checked(
                () ->
                    client.getBucketAcl(BUCKET).getGrantsAsList().stream()
                        .map(
                            g ->
                                Map.of(
                                    "granteeType",
                                    g.getGrantee().getClass().getSimpleName(),
                                    "publicGroup",
                                    g.getGrantee() instanceof GroupGrantee,
                                    "permission",
                                    g.getPermission().toString()))
                        .toList()));
        report.put(
            "prefixList",
            checked(
                () -> {
                  ListObjectsRequest q = new ListObjectsRequest();
                  q.setBucketName(BUCKET);
                  q.setPrefix(CosMaterialStore.PREFIX);
                  q.setMaxKeys(1);
                  return Map.of(
                      "requestSucceeded",
                      true,
                      "hasObjects",
                      !client.listObjects(q).getObjectSummaries().isEmpty());
                }));
        report.put(
            "retention",
            "not_verified: SDK has no bucket retention API; deletion remains disabled");
        report.put("deletionPolicyVerified", store.deletionPolicyVerified());
        report.put(
            "wechatCredential",
            checked(
                () -> {
                  String url =
                      "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="
                          + URLEncoder.encode(
                              props.getProperty("mj.wechat.app-id"),
                              java.nio.charset.StandardCharsets.UTF_8)
                          + "&secret="
                          + URLEncoder.encode(
                              props.getProperty("mj.wechat.app-secret"),
                              java.nio.charset.StandardCharsets.UTF_8);
                  HttpResponse<String> response =
                      HTTP.send(
                          HttpRequest.newBuilder(URI.create(url))
                              .timeout(Duration.ofSeconds(8))
                              .GET()
                              .build(),
                          HttpResponse.BodyHandlers.ofString());
                  JsonNode node = JSON.readTree(response.body());
                  return Map.of(
                      "http",
                      response.statusCode(),
                      "tokenIssued",
                      node.has("access_token"),
                      "errcode",
                      node.path("errcode").asInt(0),
                      "phoneAuthorizationTested",
                      false);
                }));
      } else if (args[0].equals("retention")) {
        JsonNode state = JSON.readTree(Files.readString(statePath));
        for (String scope : List.of("bucket", "object")) {
          String key = scope.equals("bucket") ? "/" : state.get("key").asString();
          GeneratePresignedUrlRequest signedRequest = new GeneratePresignedUrlRequest(BUCKET, key);
          signedRequest.setExpiration(Date.from(Instant.now().plusSeconds(120)));
          signedRequest.addRequestParameter(
              scope.equals("bucket") ? "object-lock" : "retention", "");
          report.put(
              scope,
              checked(
                  () -> {
                    HttpResponse<String> response =
                        HTTP.send(
                            HttpRequest.newBuilder(
                                    client.generatePresignedUrl(signedRequest).toURI())
                                .timeout(Duration.ofSeconds(15))
                                .GET()
                                .build(),
                            HttpResponse.BodyHandlers.ofString());
                    LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
                    result.put("http", response.statusCode());
                    DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                    factory.setFeature(
                        "http://apache.org/xml/features/disallow-doctype-decl", true);
                    Document doc =
                        factory
                            .newDocumentBuilder()
                            .parse(
                                new java.io.ByteArrayInputStream(
                                    response
                                        .body()
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    for (String name :
                        List.of("Code", "ObjectLockEnabled", "Days", "RetainUntilDate", "Mode")) {
                      NodeList nodes = doc.getElementsByTagName(name);
                      if (nodes.getLength() > 0) result.put(name, nodes.item(0).getTextContent());
                    }
                    return result;
                  }));
        }
        report.put("source", "https://intl.cloud.tencent.com/jp/document/product/436/40134");
        report.put("deleteEnabled", false);
      } else if (args[0].equals("upload")) {
        if (Files.exists(statePath))
          throw new IllegalStateException("Existing probe state: do not overwrite");
        String owner = Ids.next(),
            upload = Ids.next(),
            key = CosMaterialStore.PREFIX + "student/" + owner + "/" + upload;
        Instant at = Instant.now(),
            until = at.plus(Duration.ofHours(24)),
            expires = at.plusSeconds(120);
        Map<String, String> metadata =
            Map.of(
                "owner",
                owner,
                "purpose",
                "student_card",
                "upload-id",
                upload,
                "consent-version",
                "student-material-v1",
                "accepted-at",
                at.toString(),
                "accept-until",
                until.toString());
        byte[] bytes = Files.readAllBytes(Path.of(".local/test/native-materials/front.png"));
        store.put(key, bytes, "image/png", metadata);
        Stored object = store.head(key);
        String signed = store.sign(key, expires);
        Map<String, String> state =
            Map.of(
                "key",
                key,
                "signedUrl",
                signed,
                "createdAt",
                object.lastModified().toString(),
                "expiresAt",
                expires.toString(),
                "eligibleAt",
                object.lastModified().plus(Duration.ofHours(48)).toString());
        Files.createFile(
            statePath,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(statePath, JSON.writeValueAsString(state));
        report.put("objectKeyHash", MaterialCleanupAudit.digest(key));
        report.put("createdAt", object.lastModified().toString());
        report.put("eligibleAt", state.get("eligibleAt"));
        report.put(
            "metadataMatches", object.metadata().entrySet().containsAll(metadata.entrySet()));
        report.put("receiptHours", 24);
        report.put("signatureSeconds", 120);
        report.put(
            "anonymousHttp",
            checked(() -> get("https://" + BUCKET + ".cos.ap-shanghai.myqcloud.com/" + key)));
        report.put("signedHttp", checked(() -> get(signed)));
        report.put("tamperedSignatureHttp", checked(() -> get(signed.replace(upload, Ids.next()))));
        report.put(
            "objectAcl",
            checked(
                () ->
                    client.getObjectAcl(BUCKET, key).getGrantsAsList().stream()
                        .map(
                            g ->
                                Map.of(
                                    "publicGroup",
                                    g.getGrantee() instanceof GroupGrantee,
                                    "permission",
                                    g.getPermission().toString()))
                        .toList()));
        report.put(
            "outsidePrefixRejected",
            checked(
                () -> {
                  try {
                    store.head("other/test");
                    return false;
                  } catch (com.mujian.backend.common.api.ApiFailure e) {
                    return true;
                  }
                }));
      } else {
        JsonNode state = JSON.readTree(Files.readString(statePath));
        if (Instant.now().isBefore(Instant.parse(state.get("expiresAt").asString()).plusSeconds(5)))
          throw new IllegalStateException("wait for actual signature expiry");
        report.put("objectKeyHash", MaterialCleanupAudit.digest(state.get("key").asString()));
        report.put("signedHttpAfterExpiry", checked(() -> get(state.get("signedUrl").asString())));
        report.put("eligibleAt", state.get("eligibleAt").asString());
        report.put("deleted", false);
      }
    } catch (Exception e) {
      report.put("probeErrorType", e.getClass().getSimpleName());
    } finally {
      store.close();
      client.shutdown();
    }
    String out = System.getenv("MJ_EVIDENCE_DIR");
    if (out == null) throw new IllegalStateException("MJ_EVIDENCE_DIR required");
    Files.writeString(
        Path.of(out, "real-channel-" + args[0] + ".json"),
        JSON.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    System.out.println(JSON.writeValueAsString(report));
  }
}
