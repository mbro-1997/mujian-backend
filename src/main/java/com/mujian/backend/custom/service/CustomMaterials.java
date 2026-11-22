package com.mujian.backend.custom.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.FulfillmentReason;

import com.mujian.backend.custom.dto.CustomForms;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.custom.dto.CustomForms.EvidenceRef;
import com.mujian.backend.custom.mapper.CustomMaterialMapper;
import com.mujian.backend.custom.mapper.CustomMaterialMapper.File;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.material.service.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class CustomMaterials {
  private final CustomRequirements customRequirements;
  private final CustomPurchase customPurchase;
  private final VerificationMaterials verificationMaterials;
  private final PrivateMaterialStore store;
  private final VerificationAccess verificationAccess;
  private final FactoryAccess factoryAccess;
  private final CustomMaterialMapper customMaterialMapper;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final ObjectMapper json;
  private final com.mujian.backend.material.mapper.MaterialReferenceMapper materialReferenceMapper;

  public CustomMaterials(
      com.mujian.backend.material.mapper.MaterialReferenceMapper materialReferenceMapper,
      CustomRequirements customRequirements,
      CustomPurchase customPurchase,
      VerificationMaterials verificationMaterials,
      PrivateMaterialStore store,
      VerificationAccess verificationAccess,
      FactoryAccess factoryAccess,
      CustomMaterialMapper customMaterialMapper,
      TransactionTemplate tx,
      Clock clock,
      ObjectMapper json) {
    this.materialReferenceMapper = materialReferenceMapper;
    this.customRequirements = customRequirements;
    this.customPurchase = customPurchase;
    this.verificationMaterials = verificationMaterials;
    this.store = store;
    this.verificationAccess = verificationAccess;
    this.factoryAccess = factoryAccess;
    this.customMaterialMapper = customMaterialMapper;
    this.tx = tx;
    this.clock = clock;
    this.json = json;
  }

  public record Preview(String url, String expiresAt) {}

  /** 将材料转换为可追溯的业务引用。 */
  private EvidenceRef ref(File f) {
    return new EvidenceRef(f.id(), f.name(), f.type());
  }

  /** 收集需要关联的材料标识。 */
  public List<String> keys(String actor, String order, String purpose, List<String> ids) {
    HashMap<String,CustomMaterialMapper.File> found = new HashMap<String, File>();
    for (CustomMaterialMapper.File file : customMaterialMapper.orderFiles(List.of(order))) found.put(file.id(), file);
    ArrayList<String> keys = new ArrayList<String>();
    Set<String> seen = new HashSet<>();
    for (String id : ids) {
      CustomMaterialMapper.File referencedFile = found.get(id);
      if (referencedFile == null || !actor.equals(referencedFile.owner()) || !purpose.equals(referencedFile.purpose()) || !seen.add(id))
        throw ApiFailure.invalid();
      if (!now(clock).isBefore(referencedFile.createdAt().plusHours(24))
          && materialReferenceMapper.countCustomReferences(actor, referencedFile.storageKey()) == 0)
        throw ApiFailure.invalid();
      keys.add(referencedFile.storageKey());
    }
    return keys;
  }

  /** 为材料建立按标识查找的索引。 */
  public Map<String, EvidenceRef> index(String order) {
    return indexes(List.of(order)).getOrDefault(order, Map.of());
  }

  /** 批量建立材料索引。 */
  public Map<String, Map<String, EvidenceRef>> indexes(List<String> orders) {
    Map<String, Map<String, EvidenceRef>> byOrder = new HashMap<>();
    if (!orders.isEmpty()) {
      for (CustomMaterialMapper.File file : customMaterialMapper.orderFiles(orders)) {
        Map<String, EvidenceRef> files = byOrder.get(file.orderId());
        if (files == null) {
          files = new HashMap<>();
          byOrder.put(file.orderId(), files);
        }
        files.put(file.storageKey(), ref(file));
      }
    }
    return byOrder;
  }

  /** 读取业务对象使用的材料引用。 */
  public List<EvidenceRef> references(String stored, Map<String, EvidenceRef> byKey) {
    return stored == null
        ? List.of()
        : Arrays.stream(json.readValue(stored, String[].class))
            .map(k -> Objects.requireNonNull(byKey.get(k)))
            .toList();
  }

  /** 读取来源当前的编辑或审核状态。 */
  private String state(String actor, String role, boolean req, String root, boolean lock) {
    boolean factory = FulfillmentRole.factory.name().equals(role);
    return req
        ? customRequirements.owned(actor, factory, root, lock).state()
        : customPurchase.owned(actor, factory, root, lock).state();
  }

  /** 按原请求编号读取已完成结果。 */
  private EvidenceRef replay(String actor, String key, String digest) {
    CustomMaterialMapper.File file = customMaterialMapper.byKey(actor, key);
    if (file == null) return null;
    if (!file.digest().equals(digest)) throw conflict(D2Codes.CODE_IDEMPOTENCY_KEY_REUSED);
    return ref(file);
  }

  /** 核对当前材料是否允许写入。 */
  private void writable(String state, String purpose) {
    boolean ok =
        switch (purpose) {
          case "requirement" -> state.equals("draft");
          case "delivery" ->
              Set.of("ready_for_delivery", "delivering", "after_sale").contains(state);
          case "extension" -> Set.of("producing", "ready_for_delivery").contains(state);
          case "after_sale" -> state.equals("pending_acceptance");
          default -> false;
        };
    CustomRequirements.require(ok);
  }

  /** 核验操作范围并写入材料对象。 */
  public EvidenceRef upload(
      String actor,
      String role,
      boolean req,
      String root,
      String purpose,
      String key,
      MultipartFile file) {
    String state = state(actor, role, req, root, false);
    if (!(FulfillmentRole.factory.name().equals(role)
        ? Set.of("delivery", "extension").contains(purpose)
        : purpose.equals(req ? "requirement" : "after_sale"))) throw ApiFailure.invalid();
    if (key == null
        || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
      throw ApiFailure.invalid();
    if (file.isEmpty()) throw ApiFailure.invalid();
    if (file.getSize() > 5242880)
      throw new ApiFailure(413, D2Codes.CODE_INVALID_ARGUMENT, D2Codes.CODE_MATERIAL_TOO_LARGE);
    byte[] bytes;
    String digest;
    String name = text(file.getOriginalFilename(), 255);
    try {
      bytes = file.getBytes();
      MessageDigest hash = MessageDigest.getInstance("SHA-256");
      hash.update(json.writeValueAsBytes(List.of(role, root, purpose, name)));
      digest = HexFormat.of().formatHex(hash.digest(bytes));
    } catch (IOException e) {
      throw ApiFailure.invalid();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    CustomForms.EvidenceRef evidenceRef = replay(actor, key, digest);
    if (evidenceRef != null) return evidenceRef;
    writable(state, purpose);
    boolean pdf =
        bytes.length >= 5 && new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
    VerificationMaterials.Receipt receipt =
        verificationMaterials.uploadCustom(
            actor, new VerificationMaterials.EvidenceScope(root, purpose, role), bytes, pdf);
    return tx.execute(
        status -> {
          factoryAccess.lock(actor);
          String current = state(actor, role, req, root, true);
          CustomForms.EvidenceRef result = replay(actor, key, digest);
          if (result != null) return result;
          writable(current, purpose);
          verificationMaterials.validateNewReference(receipt);
          customMaterialMapper.insertCustomMaterial(
              receipt.uploadId(),
              actor,
              req ? root : null,
              req ? null : root,
              purpose,
              key,
              digest,
              receipt.objectKey(),
              name,
              pdf ? "file" : "image",
              now(clock));
          return new EvidenceRef(receipt.uploadId(), name, pdf ? "file" : "image");
        });
  }

  /** 核验读取权限并返回临时预览凭据。 */
  public Preview preview(String actor, String role, boolean req, String root, String id) {
    boolean admin = FulfillmentRole.admin.name().equals(role);
    if (!admin) state(actor, role, req, root, false);
    CustomMaterialMapper.File file = customMaterialMapper.selectCustomMaterialById(id);
    if (file == null || !root.equals(req ? file.requirementId() : file.orderId())) throw missing();
    if (admin) {
      boolean ext =
          verificationAccess.has(actor, "admin:custom:extension-read")
              && !file.purpose().equals("after_sale")
              && customMaterialMapper.adminScope(req ? root : null, req ? null : root, true);
      boolean sale =
          verificationAccess.has(actor, "admin:custom:after-sale-read")
              && !file.purpose().equals("extension")
              && customMaterialMapper.adminScope(req ? root : null, req ? null : root, false);
      if (!ext && !sale) throw ApiFailure.denied(D2Codes.CODE_PERMISSION_DENIED);
    }
    if (admin || !actor.equals(file.owner())) {
      boolean bound = req ? customMaterialMapper.onRequirement(id, root) : customMaterialMapper.onOrder(file.storageKey(), root);
      if (!bound
          && !(admin
              && !req
              && verificationAccess.has(actor, "admin:custom:after-sale-read")
              && customMaterialMapper.onFactoryStatement(file.storageKey(), root))) throw missing();
    }
    LocalDateTime until = now(clock).plusMinutes(5);
    try {
      return new Preview(store.sign(file.storageKey(), until.atZone(ZONE).toInstant()), time(until));
    } catch (ApiFailure failure) {
      if (D2Codes.CODE_OBJECT_STORAGE_UNAVAILABLE.equals(failure.reason))
        throw unavailable(FulfillmentReason.PRIVATE_STORAGE_UNAVAILABLE.name(), failure);
      throw failure;
    }
  }
}
