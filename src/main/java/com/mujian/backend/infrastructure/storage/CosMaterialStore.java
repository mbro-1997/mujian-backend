package com.mujian.backend.infrastructure.storage;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.qcloud.cos.*;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.*;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.BucketLifecycleConfiguration;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.time.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class CosMaterialStore implements PrivateMaterialStore {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CosMaterialStore.class);
  public static final String PREFIX = "dev/d2-a/verification/";
  public static final String CUSTOM_PREFIX="dev/d6/custom-material/";
  public static final String ORDER_EVIDENCE_PREFIX = "dev/d4/order-evidence/";
  public static final String FACTORY_PREFIX = "dev/d2-b/factory/";
  private final Environment env;
  private COSClient client;

  public CosMaterialStore(Environment e) {
    env = e;
  }

  /** 取得对象存储客户端。 */
  private synchronized COSClient client() {
    if (client != null) return client;
    String id = env.getProperty("mj.cos.secret-id", ""),
        key = env.getProperty("mj.cos.secret-key", "");
    if (id.isBlank() || key.isBlank()) throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE);
    ClientConfig cfg = new ClientConfig(new Region("ap-shanghai"));
    cfg.setHttpProtocol(HttpProtocol.https);
    cfg.setConnectionTimeout(3000);
    cfg.setSocketTimeout(15000);
    cfg.setMaxErrorRetry(0);
    client = new COSClient(new BasicCOSCredentials(id, key), cfg);
    return client;
  }

  /** 读取本环境配置的存储桶。 */
  private String bucket() {
    return "cct1-1429809468";
  }

  /** 验证受保护对象的路径和访问范围。 */
  private void check(String key) {
    if (!key.matches("dev/d2-a/verification/(realname|student)/[0-9a-f]{32}/[0-9a-f]{32}")
        && !key.matches(CUSTOM_PREFIX + "[0-9a-f]{32}/[0-9a-f]{32}")
        && !key.matches(ORDER_EVIDENCE_PREFIX + "[0-9a-f]{32}/[0-9a-f]{32}")
        && !key.matches("dev/d2-b/factory/[0-9a-f]{32}/[0-9a-f]{32}")
        && !key.matches("dev/d2-c/(props|qr-evidence)/[0-9a-f]{32}/[0-9a-f]{32}")
        && !key.matches("dev/d2-c/qr-images/[0-9a-f]{32}/[0-9a-f]{64}\\.png"))
      throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE);
  }

  /** 将已校验的对象内容写入指定路径。 */
  public void put(String key, byte[] bytes, String type, Map<String, String> metadata) {
    check(key);
    try {
      ObjectMetadata m = new ObjectMetadata();
      m.setContentLength(bytes.length);
      m.setContentType(type);
      m.setUserMetadata(metadata);
      PutObjectRequest r = new PutObjectRequest(bucket(), key, new ByteArrayInputStream(bytes), m);
      r.setCannedAcl(CannedAccessControlList.Private);
      client().putObject(r);
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE, e);
    }
  }

  /** 读取对象元数据以验证上传结果。 */
  public Stored head(String key) {
    check(key);
    try {
      ObjectMetadata m = client().getObjectMetadata(bucket(), key);
      return new Stored(
          key, m.getUserMetadata(), m.getLastModified().toInstant(), m.getVersionId());
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE, e);
    }
  }

  /** 为已授权的对象生成限时访问地址。 */
  public String sign(String key, Instant expires) {
    check(key);
    try {
      return client().generatePresignedUrl(bucket(), key, Date.from(expires)).toString();
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE, e);
    }
  }

  /** 按前缀和游标列举待核查的对象。 */
  public Page list(String marker) {
    try {
      ListObjectsRequest r = new ListObjectsRequest();
      r.setBucketName(bucket());
      // QR image orphans require their own reconciliation; this material scan never lists them.
      List<String> prefixes =
          List.of(
              PREFIX,
              FACTORY_PREFIX,
              "dev/d2-c/props/",
              "dev/d2-c/qr-evidence/",
              ORDER_EVIDENCE_PREFIX,CUSTOM_PREFIX);
      int index = 0;
      String cursor = marker;
      if (marker != null && marker.matches("[BCDEF]:.*")) {
        index = marker.charAt(0) - 'A';
        cursor = marker.substring(2);
      }
      r.setPrefix(prefixes.get(index));
      r.setMaxKeys(200);
      r.setMarker(cursor);
      ObjectListing p = client().listObjects(r);
      String next =
          p.isTruncated()
              ? (index == 0 ? "" : String.valueOf((char) ('A' + index)) + ":") + p.getNextMarker()
              : index + 1 < prefixes.size() ? String.valueOf((char) ('A' + index + 1)) + ":" : null;
      return new Page(
          p.getObjectSummaries().stream()
              .map(o -> new Stored(o.getKey(), Map.of(), o.getLastModified().toInstant(), null))
              .toList(),
          next);
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE, e);
    }
  }

  // Safe default: deletion remains report-only until cloud version/retention/lifecycle and CL gates
  // are evidenced.
  /** 检查对象是否满足允许删除的配置约束。 */
  public boolean deletionPolicyVerified() {
    if (!env.getProperty("mj.verification.cleanup.cloud-policy-verified", Boolean.class, false))
      return false;
    try { // Versioned/unknown retention requires a separately evidenced implementation; never
      // create delete markers.
      if (!BucketVersioningConfiguration.OFF.equals(
          client().getBucketVersioningConfiguration(bucket()).getStatus())) return false;
      BucketLifecycleConfiguration lifecycle = client().getBucketLifecycleConfiguration(bucket());
      return lifecycle == null
          || lifecycle.getRules() == null
          || lifecycle.getRules().stream().noneMatch(r -> "Enabled".equals(r.getStatus()));
    } catch (Exception e) {
      return false;
    }
  }

  /** 删除已确认可清理的指定对象。 */
  public void delete(String key, String versionId) {
    check(key);
    if (!deletionPolicyVerified()) throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE);
    try {
      if (versionId == null) client().deleteObject(bucket(), key);
      else client().deleteVersion(bucket(), key, versionId);
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE, e);
    }
  }

  /** 释放本次调用占用的资源。 */
  @PreDestroy
  public void close() {
    if (client != null) client.shutdown();
  }
}
