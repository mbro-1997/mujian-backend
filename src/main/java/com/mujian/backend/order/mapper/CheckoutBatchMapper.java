package com.mujian.backend.order.mapper;

import com.mujian.backend.order.domain.BatchItemSnapshotDO;
import com.mujian.backend.order.domain.BatchTransportSnapshotDO;
import com.mujian.backend.order.domain.CheckoutBatchDO;
import com.mujian.backend.order.dto.CheckoutWrites;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CheckoutBatchMapper {
  /** 接受支付调起/取消命令时更新批次版本，不修改金额或有效期。 */
  int touchBatch(
      @Param("id") String id, @Param("version") int version, @Param("now") LocalDateTime now);

  /** 安全关闭永久释放guard，晚到资金不得重新激活它。 */
  void closeBatch(
      @Param("id") String id, @Param("status") String status, @Param("now") LocalDateTime now);

  /** 恢复清单只允许原批次仍拥有当前指针。 */
  int restoreChecklist(
      @Param("id") String id, @Param("batch") String batch, @Param("now") LocalDateTime now);

  /** 未成单退款处理中保持不可转成单的恢复方向。 */
  void markBatchRefunding(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 按批次读取全部冻结选品，避免逐厂逐项回查当前SKU。 */
  List<BatchItemSnapshotDO> selectItemSnapshotsByBatch(
      @Param("id") String id, @Param("factoryId") String factoryId);

  /** 按批次读取冻结往返运输和收件地址。 */
  List<BatchTransportSnapshotDO> selectTransportSnapshotsByBatch(
      @Param("id") String id, @Param("factoryId") String factoryId);

  /** 全付成单一起标记批次已付及永久释放活动guard。 */
  void markBatchPaid(
      @Param("id") String id,
      @Param("paidAt") LocalDateTime paidAt,
      @Param("now") LocalDateTime now);

  /** 仅原批次仍持有清单时转换，旧批次不能覆盖新的清单。 */
  int markChecklistConverted(
      @Param("id") String id, @Param("batch") String batch, @Param("now") LocalDateTime now);

  /** 资金异常恢复方式单向切换至退款，旧guard不会重获。 */
  void markRecovery(
      @Param("id") String id,
      @Param("mode") String mode,
      @Param("reason") String reason,
      @Param("now") LocalDateTime now);

  /** 按原批次查询并按固定根顺序加锁。 */
  CheckoutBatchDO selectBatchById(@Param("id") String id, @Param("lock") boolean lock);

  /** 创建不可变提交根，唯一guard保护原清单。 */
  void insertBatch(@Param("batch") CheckoutWrites.Batch batch);

  /** 先保存每厂运输快照，再写引用它的明细。 */
  void insertTransportSnapshot(@Param("transport") CheckoutWrites.Transport transport);

  /** 保存锁内确定的价格及SN分配。 */
  void insertItemSnapshot(@Param("item") CheckoutWrites.Item item);

  /** 原清单当前版本推进支付中，不改变租期或成员。 */
  int markChecklistProcessing(
      @Param("actor") String actor,
      @Param("id") String id,
      @Param("version") int version,
      @Param("batch") String batch,
      @Param("now") LocalDateTime now);

  /** 读取批次涉及工厂，用于后续恢复的统一根锁。 */
  List<String> selectFactoryIdsByBatch(@Param("id") String id);
}
