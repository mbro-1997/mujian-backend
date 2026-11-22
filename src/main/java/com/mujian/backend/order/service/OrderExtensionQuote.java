package com.mujian.backend.order.service;

import com.mujian.backend.delivery.service.DisputeGuard;

import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.ExtensionReason;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.mapper.OrderExtensionMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderExtensionQuote {
  public record Quote(String orderId,int orderVersion,LocalDate originalDueDate,LocalDate requestedDueDate,
      String latestPaidAt,String serverTime,int maxExtraDays,int extraDays,int rentFen) {}

  private final RentalOrderAccess rentalOrderAccess;
  private final FactoryAccess factoryAccess;
  private final com.mujian.backend.delivery.service.DisputeGuard guard;
  private final OrderExtensionMapper orderExtensionMapper;
  private final Clock clock;

  /** 复用订单归属和可信业务时钟。 */
  public OrderExtensionQuote(RentalOrderAccess rentalOrderAccess,FactoryAccess factoryAccess,
      OrderExtensionMapper orderExtensionMapper,Clock clock,com.mujian.backend.delivery.service.DisputeGuard guard) {
    this.guard=guard;this.rentalOrderAccess=rentalOrderAccess;
    this.factoryAccess=factoryAccess;
    this.orderExtensionMapper=orderExtensionMapper;
    this.clock=clock;
  }

  /** 租期含首尾日，新增占用只从原缓冲末日之后开始。 */
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public Quote quote(String actor,String orderId,int extraDays) {
    RentalOrderDO order = rentalOrderAccess.owned(actor,id(orderId),false,false);
    factoryAccess.context(actor,true);
    DisputeGuard.Facts facts=guard.inspect(orderId);
    if(!facts.ready() || facts.paused() || facts.claimActive() || facts.terminationActive())throw conflict(ExtensionReason.EXTENSION_WINDOW_CLOSED.name());
    if (extraDays<1 || extraDays>120) throw ApiFailure.invalid();
    LocalDate dueDate = order.currentDueDate();
    LocalDateTime latestPaidAt = com.mujian.backend.delivery.service.OrderDisputeContext.latestPaidAt(dueDate);
    LocalDateTime at = now(clock);
    if (at.isAfter(latestPaidAt)) throw conflict(ExtensionReason.EXTENSION_WINDOW_CLOSED.name());
    OrderExtensionMapper.QuoteBasis basis = orderExtensionMapper.selectExtensionQuoteBasis(orderId,at.toLocalDate());
    if (!basis.renting()) throw conflict(ExtensionReason.EXTENSION_WINDOW_CLOSED.name());
    int maxExtraDays = Math.toIntExact(119-ChronoUnit.DAYS.between(order.rentalStartDate(),dueDate));
    if (basis.snLimitDays()!=null) maxExtraDays=Math.min(maxExtraDays,basis.snLimitDays());
    if (extraDays>maxExtraDays) throw conflict(ExtensionReason.EXTENSION_DAYS_EXCEEDED.name());
    int rentFen = Math.toIntExact(Math.multiplyExact(basis.dailyRentFen(),extraDays));
    return new Quote(orderId,order.version(),dueDate,dueDate.plusDays(extraDays),time(latestPaidAt),
        time(at),maxExtraDays,extraDays,rentFen);
  }
}
