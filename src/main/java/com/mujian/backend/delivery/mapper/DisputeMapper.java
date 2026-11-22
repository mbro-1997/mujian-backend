package com.mujian.backend.delivery.mapper;

import com.mujian.backend.delivery.service.DisputeGuard;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DisputeMapper {
  record Source(String kind, String id) {}

  /** 只返回该访问者在本单可见的真实来源，不展开私有案情。 */
  java.util.List<Source> selectSources(@Param("orderId") String orderId, @Param("factory") boolean factory,
      @Param("admin") boolean admin);

  /** 原订单根内读取问题、索赔、仲裁、终止和核验停表事实。 */
  DisputeGuard.Facts selectFactsByOrderId(@Param("orderId") String orderId);

  record OrderFacts(String id, boolean ready, boolean paused, boolean claimActive, boolean terminationActive) {}

  /** 按本页订单一次读取互斥事实。 */
  java.util.List<OrderFacts> selectPageFacts(@Param("ids") java.util.List<String> ids);

  /** 恢复只忽略自身保存的时钟，所有业务来源仍按原门禁检查。 */
  boolean terminationSettled(@Param("orderId") String orderId);
  /** 按 orderId 查询 order_return_check 记录。 */
  boolean canResumeReceipt(@Param("orderId") String orderId);

  /** 仅返回所属订单中不能走普通履约的订单号。 */
  Set<String> selectBlockedOrderIds(@Param("buyerId") String buyerId,@Param("factoryId") String factoryId);
}
