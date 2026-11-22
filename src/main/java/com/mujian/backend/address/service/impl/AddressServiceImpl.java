package com.mujian.backend.address.service.impl;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.planning.utils.PlanningValues.*;

import com.mujian.backend.address.domain.ShippingAddressDO;
import com.mujian.backend.address.dto.AddressDtos.*;
import com.mujian.backend.address.mapper.AddressMapper;
import com.mujian.backend.address.service.AddressService;
import com.mujian.backend.address.vo.AddressViews.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.service.PlanningOperations;
import com.mujian.backend.planning.vo.PlanningViews.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AddressServiceImpl implements AddressService {
  private final AddressMapper addressMapper;
  private final PlanningOperations planningOperations;
  private final Clock clock;

  /** 复用用户根锁及操作回执。 */
  public AddressServiceImpl(
      AddressMapper addressMapper, PlanningOperations planningOperations, Clock clock) {
    this.addressMapper = addressMapper;
    this.planningOperations = planningOperations;
    this.clock = clock;
  }

  /** 查询有效地址，默认优先，不泄漏软删记录。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<Address> list(PlanningQuery planningQuery) {
    planningOperations.readable(planningQuery.actor());
    long total = addressMapper.selectAddressCountByActor(planningQuery.actor());
    List<Address> addressListItems = new ArrayList<>();
    if (planningQuery.offset() < total)
      for (ShippingAddressDO shippingAddressDO :
          addressMapper.selectAddressPageByQuery(planningQuery))
        addressListItems.add(
            new Address(
                shippingAddressDO.id(),
                shippingAddressDO.receiverName(),
                shippingAddressDO.phone(),
                shippingAddressDO.regionText(),
                shippingAddressDO.detailAddress(),
                shippingAddressDO.isDefault(),
                shippingAddressDO.version(),
                time(shippingAddressDO.createdAt()),
                time(shippingAddressDO.updatedAt())));
    return PageResult.of(addressListItems, total, planningQuery.paging());
  }

  /** 读取本人有效地址，不修改登录手机号。 */
  @Transactional(readOnly = true)
  public Address detail(String actor, String id) {
    planningOperations.readable(actor);
    ShippingAddressDO shippingAddressDO = addressMapper.selectOwnedAddressById(actor, id, false);
    if (shippingAddressDO == null
        || !AddressStatus.active.name().equals(shippingAddressDO.addressStatus())) throw missing();
    return new Address(
        shippingAddressDO.id(),
        shippingAddressDO.receiverName(),
        shippingAddressDO.phone(),
        shippingAddressDO.regionText(),
        shippingAddressDO.detailAddress(),
        shippingAddressDO.isDefault(),
        shippingAddressDO.version(),
        time(shippingAddressDO.createdAt()),
        time(shippingAddressDO.updatedAt()));
  }

  /** 默认仅按显式开关设置，首条地址也不自动设默认。 */
  public Receipt create(PlanningCommand planningCommand, CreateAddress createAddress) {
    planningOperations.lock(planningCommand.actor());
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    String id = Ids.next();
    LocalDateTime now = now(clock);
    if (createAddress.isDefault())
      addressMapper.clearOtherAddressDefaultsByActor(planningCommand.actor(), id, now);
    ShippingAddressDO shippingAddressDO =
        new ShippingAddressDO(
            id,
            planningCommand.actor(),
            text(createAddress.receiverName(), 64),
            createAddress.phone(),
            text(createAddress.regionText(), 128),
            text(createAddress.detailAddress(), 255),
            createAddress.isDefault(),
            AddressStatus.active.name(),
            0,
            null,
            now,
            now);
    addressMapper.insertAddress(shippingAddressDO);
    return planningOperations.record(planningCommand, PlanningResource.address, id, 0);
  }

  /** 所有字段与默认切换在一个事务保存。 */
  public Receipt update(
      PlanningCommand planningCommand, String id, UpdateAddress updateAddressInput) {
    planningOperations.lock(planningCommand.actor());
    ShippingAddressDO shippingAddressDOInitial =
        addressMapper.selectOwnedAddressById(planningCommand.actor(), id, true);
    if (shippingAddressDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    if (!AddressStatus.active.name().equals(shippingAddressDOInitial.addressStatus()))
      throw missing();
    version(shippingAddressDOInitial.version(), updateAddressInput.expectedVersion());
    LocalDateTime now = now(clock);
    if (updateAddressInput.isDefault())
      addressMapper.clearOtherAddressDefaultsByActor(planningCommand.actor(), id, now);
    ShippingAddressDO shippingAddressDO =
        new ShippingAddressDO(
            id,
            planningCommand.actor(),
            text(updateAddressInput.receiverName(), 64),
            updateAddressInput.phone(),
            text(updateAddressInput.regionText(), 128),
            text(updateAddressInput.detailAddress(), 255),
            updateAddressInput.isDefault(),
            shippingAddressDOInitial.addressStatus(),
            next(shippingAddressDOInitial.version()),
            null,
            shippingAddressDOInitial.createdAt(),
            now);
    addressMapper.updateAddress(shippingAddressDO);
    return planningOperations.record(
        planningCommand, PlanningResource.address, id, shippingAddressDO.version());
  }

  /** 删除默认允许无默认；旧请求在软删后仍回放原回执。 */
  public Receipt changeState(
      PlanningCommand planningCommand,
      String id,
      ExpectedVersion expectedVersionInput,
      boolean delete) {
    planningOperations.lock(planningCommand.actor());
    ShippingAddressDO shippingAddressDOInitial =
        addressMapper.selectOwnedAddressById(planningCommand.actor(), id, true);
    if (shippingAddressDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    if (!AddressStatus.active.name().equals(shippingAddressDOInitial.addressStatus()))
      throw missing();
    version(shippingAddressDOInitial.version(), expectedVersionInput.expectedVersion());
    if (!delete && shippingAddressDOInitial.isDefault())
      return planningOperations.record(
          planningCommand, PlanningResource.address, id, shippingAddressDOInitial.version());
    LocalDateTime now = now(clock);
    if (!delete) addressMapper.clearOtherAddressDefaultsByActor(planningCommand.actor(), id, now);
    ShippingAddressDO shippingAddressDO =
        new ShippingAddressDO(
            id,
            planningCommand.actor(),
            shippingAddressDOInitial.receiverName(),
            shippingAddressDOInitial.phone(),
            shippingAddressDOInitial.regionText(),
            shippingAddressDOInitial.detailAddress(),
            !delete,
            delete ? AddressStatus.deleted.name() : shippingAddressDOInitial.addressStatus(),
            next(shippingAddressDOInitial.version()),
            null,
            shippingAddressDOInitial.createdAt(),
            now);
    addressMapper.updateAddress(shippingAddressDO);
    return planningOperations.record(
        planningCommand, PlanningResource.address, id, shippingAddressDO.version());
  }
}
