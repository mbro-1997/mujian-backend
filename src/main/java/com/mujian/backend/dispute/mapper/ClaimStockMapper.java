package com.mujian.backend.dispute.mapper;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
@Mapper
public interface ClaimStockMapper {
  record Line(String reservationId,String itemId,String skuId,String name,String snId,String snCode,int heldQuantity,int unrecordedQuantity) {}
  record Fact(String id,String claimId,String reservationId,String itemId,String skuId,String snId,int quantity,String outcome,String status,String actor,String remark,
      String sourceType,String decisionId,LocalDateTime createdAt,LocalDateTime appliedAt) {}
  /** 按 claim 查询 order_claim_stock_result 记录。 */
  List<Line> selectOrderClaimLines(@Param("claim") String claim);
  /** 按 claim 查询 order_claim_stock_result 记录。 */
  List<Fact> selectOrderClaimStockResultFacts(@Param("claim") String claim);
  /** 按 fact, replaces 新增 order_claim_stock_result 记录。 */
  void insertClaimStockResult(@Param("fact") Fact fact,@Param("replaces") String replaces);
  /** 按 id, claim 更新 order_claim_stock_result 记录。 */
  int supersedeClaimStockResult(@Param("id") String id,@Param("claim") String claim);
  /** 按 id, version, at 更新 order_claim 记录。 */
  int advanceClaimVersion(@Param("id") String id,@Param("version") int version,@Param("at") LocalDateTime at);
  /** 按 child, fact, at 新增 inventory_reservation 记录。 */
  int insertInventoryReservationSplit(@Param("child") String child,@Param("fact") Fact fact,@Param("at") LocalDateTime at);
  /** 按 fact, at 更新 inventory_reservation 记录。 */
  int updateInventoryReservationReduce(@Param("fact") Fact fact,@Param("at") LocalDateTime at);
  /** 按 sku, quantity, lost 更新 prop_sku_stock 记录。 */
  int updatePropSkuStockLoss(@Param("sku") String sku,@Param("quantity") int quantity,@Param("lost") boolean lost,@Param("scrap") boolean scrap,@Param("at") LocalDateTime at);
  /** 按 id, release, at 更新 order_claim_stock_result 记录。 */
  void updateOrderClaimStockResultApplied(@Param("id") String id,@Param("release") String release,@Param("at") LocalDateTime at);
  /** 按 id 更新 order_claim_stock_result 记录。 */
  void repairPending(@Param("id") String id);
  /** 按 after 查询 order_claim 记录。 */
  List<String> selectClaimIdsForRecovery(@Param("after") String after);
  /** 按 id 查询 arbitration_case 记录。 */
  boolean arbitrationClosed(@Param("id") String id);
  /** 按 id 查询 order_claim 记录。 */
  boolean selectClaimStockCloseable(@Param("id") String claim);
  /** 按 claim, reservation, remaining 更新 order_claim_stock_result 记录。 */
  void replaceDisplaced(@Param("claim") String claim,@Param("reservation") String reservation,@Param("remaining") int remaining);
  /** 按 id 查询 rental_order 记录。 */
  boolean fundsComplete(@Param("id") String id);
  /** 按 order, actor, at 更新 order_return_check 记录。 */
  int closeCheck(@Param("order") String order,@Param("actor") String actor,@Param("at") LocalDateTime at);
}
