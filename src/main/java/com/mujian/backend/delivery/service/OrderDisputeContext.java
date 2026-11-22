package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.time;

import com.mujian.backend.delivery.mapper.DisputeMapper;
import com.mujian.backend.delivery.vo.DisputeContext;
import com.mujian.backend.order.domain.RentalOrderDO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderDisputeContext {
  private final OverdueFees overdueFees;
  private final DisputeMapper disputeMapper;

  /** 来源以订单归属和访问角色过滤，读取方先完成鉴权。 */
  public OrderDisputeContext(DisputeMapper disputeMapper,OverdueFees overdueFees) {
    this.overdueFees=overdueFees;
    this.disputeMapper = disputeMapper;
  }

  /** 原合同日期保留在订单，对外归还日和延期付款截止使用当前有效日期。 */
  public DisputeContext read(RentalOrderDO order, boolean factory, boolean admin) {
    List<DisputeMapper.Source> sources = disputeMapper.selectSources(order.id(), factory, admin);
    LocalDate dueDate = order.currentDueDate();
    return new DisputeContext(ids(sources, "issue"), ids(sources, "extension").stream().findFirst().orElse(null),
        ids(sources, "claim"), ids(sources, "arbitration"), ids(sources, "receivable"),
        dueDate.toString(), time(latestPaidAt(dueDate)),overdueFees.read(order.id()));
  }

  /** 读取最近一次实际付款时间。 */
  public static LocalDateTime latestPaidAt(LocalDate dueDate) {
    return dueDate.plusDays(1).atStartOfDay().minusHours(48);
  }

  /** 提取关联记录标识供批量读取。 */
  private static List<String> ids(List<DisputeMapper.Source> sources, String kind) {
    return sources.stream().filter(row -> kind.equals(row.kind())).map(DisputeMapper.Source::id).toList();
  }
}
