package com.mujian.backend.common.dictionary;

import com.mujian.backend.common.dictionary.mapper.OptionDictionaryMapper;
import com.mujian.backend.common.dictionary.projection.OptionDictionaryProjection;
import com.mujian.backend.common.enums.DictionaryGroup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 进程内唯一启用字典缓存；不可变快照整体发布，不缓存停用项。 */
@Component
public class DictionaryCache {
  private static final Logger logger = LoggerFactory.getLogger(DictionaryCache.class);
  private final OptionDictionaryMapper optionDictionaryMapper;
  private final TransactionTemplate transactionTemplate;

  // 字典组 →（选项编码 → 显示名称）
  private volatile Map<DictionaryGroup, Map<String, String>> dictionaryMap;

  /** 装配查询和独立只读事务，避免提交回调沿用已结束事务的连接。 */
  public DictionaryCache(
      OptionDictionaryMapper optionDictionaryMapper,
      PlatformTransactionManager platformTransactionManager) {
    this.optionDictionaryMapper = optionDictionaryMapper;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setReadOnly(true);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 首次加载失败直接传播，由现有启动流程终止启动。 */
  public void initialize() {
    loadAndReplace();
  }

  /** 返回同一不可变快照，调用方在本次业务处理中持有并复用。 */
  public Map<DictionaryGroup, Map<String, String>> snapshot() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      for (TransactionSynchronization transactionSynchronization :
          TransactionSynchronizationManager.getSynchronizations()) {
        if (transactionSynchronization
            instanceof DictionarySnapshotSynchronization dictionarySnapshotSynchronization) {
          return dictionarySnapshotSynchronization.dictionarySnapshot;
        }
      }
    }
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryMap;
    if (dictionarySnapshot == null) throw new IllegalStateException("字典尚未初始化");
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new DictionarySnapshotSynchronization(dictionarySnapshot));
    }
    return dictionarySnapshot;
  }

  /** 手动或提交后刷新；失败保留旧快照，并返回失败供调用方识别。 */
  public boolean refresh() {
    try {
      loadAndReplace();
      return true;
    } catch (RuntimeException runtimeException) {
      logger.error(
          "event=dictionary_refresh_failed action=retain_previous_snapshot", runtimeException);
      return false;
    }
  }

  /** 在字典写事务内登记提交后刷新；回滚不会执行，禁止无事务提前刷新。 */
  public void refreshAfterCommit() {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive()) {
      throw new IllegalStateException("字典变更必须在事务中登记提交后刷新");
    }
    for (TransactionSynchronization transactionSynchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      if (transactionSynchronization instanceof DictionaryRefreshSynchronization) return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new DictionaryRefreshSynchronization());
  }

  /** 串行加载并整体替换，避免慢的旧刷新覆盖新的刷新结果。 */
  private synchronized void loadAndReplace() {
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot =
        transactionTemplate.execute(
            transactionStatus -> {
              List<String> groups = new ArrayList<>();
              Map<DictionaryGroup, Map<String, String>> dictionaryGroupMap =
                  new EnumMap<>(DictionaryGroup.class);
              for (DictionaryGroup dictionaryGroup : DictionaryGroup.values()) {
                groups.add(dictionaryGroup.code());
                dictionaryGroupMap.put(dictionaryGroup, new LinkedHashMap<>());
              }
              List<OptionDictionaryProjection> optionDictionaryProjectionList =
                  optionDictionaryMapper.selectEnabledOptionDictionaryListByGroups(groups);
              for (OptionDictionaryProjection optionDictionaryProjection :
                  optionDictionaryProjectionList) {
                DictionaryGroup dictionaryGroup =
                    DictionaryGroup.fromCode(optionDictionaryProjection.optionGroup());
                String code = optionDictionaryProjection.code();
                String label = optionDictionaryProjection.label();
                if (optionDictionaryProjection.enabled() != 1
                    || code == null
                    || label == null
                    || dictionaryGroupMap.get(dictionaryGroup).containsKey(code)) {
                  throw new IllegalStateException("字典启用项数据不完整或重复");
                }
                dictionaryGroupMap.get(dictionaryGroup).put(code, label);
              }
              for (DictionaryGroup dictionaryGroup : DictionaryGroup.values()) {
                dictionaryGroupMap.put(
                    dictionaryGroup,
                    Collections.unmodifiableMap(dictionaryGroupMap.get(dictionaryGroup)));
              }
              return Collections.unmodifiableMap(dictionaryGroupMap);
            });
    if (dictionarySnapshot == null) throw new IllegalStateException("字典加载未返回快照");
    dictionaryMap = dictionarySnapshot;
  }

  /** 事务内仅持有启用缓存的同一引用，随Spring事务结束释放。 */
  private static final class DictionarySnapshotSynchronization
      implements TransactionSynchronization {
    private final Map<DictionaryGroup, Map<String, String>> dictionarySnapshot;

    /** 固定本次业务使用的字典版本，不复制或缓存停用数据。 */
    private DictionarySnapshotSynchronization(
        Map<DictionaryGroup, Map<String, String>> dictionarySnapshot) {
      this.dictionarySnapshot = dictionarySnapshot;
    }
  }

  /** 每个写事务只登记一次，提交完成后重新读取最新已提交字典。 */
  private final class DictionaryRefreshSynchronization implements TransactionSynchronization {
    /** 提交成功后刷新，失败仅保留旧缓存，不伪装事务回滚。 */
    @Override
    public void afterCommit() {
      refresh();
    }
  }
}
