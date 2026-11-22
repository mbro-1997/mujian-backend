package com.mujian.backend.common.dictionary;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.common.dictionary.mapper.OptionDictionaryMapper;
import com.mujian.backend.common.dictionary.projection.OptionDictionaryProjection;
import com.mujian.backend.common.enums.DictionaryGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DictionaryCacheTest {
  /** 验证快照只读、组完整、有序，重复业务读取不再调用Mapper。 */
  @Test
  void immutableOrderedSnapshotDoesNotQueryAgain() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    when(optionDictionaryMapper.selectEnabledOptionDictionaryListByGroups(anyList()))
        .thenReturn(
            List.of(
                new OptionDictionaryProjection("prop_sku-type", "chair", "椅子", 1),
                new OptionDictionaryProjection("prop_sku-type", "table", "桌子", 1),
                new OptionDictionaryProjection("prop_sku-fire_resistant", "no", "不防火", 1)));
    DictionaryCache dictionaryCache =
        new DictionaryCache(optionDictionaryMapper, new TestTransactionManager());
    dictionaryCache.initialize();
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryCache.snapshot();
    assertEquals(3, dictionarySnapshot.size());
    assertEquals(
        List.of("chair", "table"),
        new ArrayList<>(dictionarySnapshot.get(DictionaryGroup.PROP_SKU_TYPE).keySet()));
    assertEquals("不防火", dictionarySnapshot.get(DictionaryGroup.PROP_SKU_FIRE_RESISTANT).get("no"));
    assertThrows(UnsupportedOperationException.class, () -> dictionarySnapshot.clear());
    assertThrows(
        UnsupportedOperationException.class,
        () -> dictionarySnapshot.get(DictionaryGroup.PROP_SKU_TYPE).clear());
    for (int i = 0; i < 10; i++) assertSame(dictionarySnapshot, dictionaryCache.snapshot());
    verify(optionDictionaryMapper, times(1)).selectEnabledOptionDictionaryListByGroups(anyList());
  }

  /** 验证新快照整体发布，已取得的旧快照不随刷新改变。 */
  @Test
  void refreshPublishesNewSnapshotAtomically() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    when(optionDictionaryMapper.selectEnabledOptionDictionaryListByGroups(anyList()))
        .thenReturn(List.of(new OptionDictionaryProjection("prop_sku-type", "chair", "旧名", 1)))
        .thenReturn(List.of(new OptionDictionaryProjection("prop_sku-type", "chair", "新名", 1)));
    DictionaryCache dictionaryCache =
        new DictionaryCache(optionDictionaryMapper, new TestTransactionManager());
    dictionaryCache.initialize();
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryCache.snapshot();
    assertTrue(dictionaryCache.refresh());
    assertEquals("旧名", dictionarySnapshot.get(DictionaryGroup.PROP_SKU_TYPE).get("chair"));
    assertEquals("新名", dictionaryCache.snapshot().get(DictionaryGroup.PROP_SKU_TYPE).get("chair"));
  }

  /** 验证提交后才刷新，同一事务重复登记只执行一次。 */
  @Test
  void commitRefreshesOnceAndRollbackDoesNotRefresh() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    when(optionDictionaryMapper.selectEnabledOptionDictionaryListByGroups(anyList()))
        .thenReturn(List.of());
    TestTransactionManager testTransactionManager = new TestTransactionManager();
    DictionaryCache dictionaryCache =
        new DictionaryCache(optionDictionaryMapper, testTransactionManager);
    dictionaryCache.initialize();
    TransactionTemplate transactionTemplate = new TransactionTemplate(testTransactionManager);
    transactionTemplate.executeWithoutResult(
        transactionStatus -> {
          dictionaryCache.refreshAfterCommit();
          dictionaryCache.refreshAfterCommit();
          verify(optionDictionaryMapper, times(1))
              .selectEnabledOptionDictionaryListByGroups(anyList());
        });
    verify(optionDictionaryMapper, times(2)).selectEnabledOptionDictionaryListByGroups(anyList());
    transactionTemplate.executeWithoutResult(
        transactionStatus -> {
          dictionaryCache.refreshAfterCommit();
          transactionStatus.setRollbackOnly();
        });
    verify(optionDictionaryMapper, times(2)).selectEnabledOptionDictionaryListByGroups(anyList());
    assertThrows(IllegalStateException.class, dictionaryCache::refreshAfterCommit);
  }

  /** 验证首次加载失败向启动流程传播，后续失败保留旧缓存。 */
  @Test
  void initialFailurePropagatesAndRefreshFailureRetainsOldSnapshot() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    when(optionDictionaryMapper.selectEnabledOptionDictionaryListByGroups(anyList()))
        .thenThrow(new IllegalStateException("测试加载失败"))
        .thenReturn(List.of())
        .thenThrow(new IllegalStateException("测试刷新失败"));
    DictionaryCache dictionaryCache =
        new DictionaryCache(optionDictionaryMapper, new TestTransactionManager());
    assertThrows(IllegalStateException.class, dictionaryCache::initialize);
    assertThrows(IllegalStateException.class, dictionaryCache::snapshot);
    dictionaryCache.initialize();
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryCache.snapshot();
    assertFalse(dictionaryCache.refresh());
    assertSame(dictionarySnapshot, dictionaryCache.snapshot());
  }

  /** 验证构建中遇到非法数据时，不发布残缺快照。 */
  @Test
  void incompleteRefreshNeverPublishesPartialMap() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    when(optionDictionaryMapper.selectEnabledOptionDictionaryListByGroups(anyList()))
        .thenReturn(List.of())
        .thenReturn(
            List.of(
                new OptionDictionaryProjection("prop_sku-type", "chair", "椅子", 1),
                new OptionDictionaryProjection("prop_sku-type", "chair", "重复项", 1)));
    DictionaryCache dictionaryCache =
        new DictionaryCache(optionDictionaryMapper, new TestTransactionManager());
    dictionaryCache.initialize();
    Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryCache.snapshot();
    assertFalse(dictionaryCache.refresh());
    assertSame(dictionarySnapshot, dictionaryCache.snapshot());
  }

  /** 并发刷新后，同一业务事务继续使用原快照，事务外读取新快照。 */
  @Test
  void transactionKeepsItsSnapshotAcrossRefresh() {
    OptionDictionaryMapper optionDictionaryMapper = mock(OptionDictionaryMapper.class);
    when(optionDictionaryMapper.selectEnabledOptionDictionaryListByGroups(anyList()))
        .thenReturn(List.of(new OptionDictionaryProjection("prop_sku-type", "chair", "旧名", 1)))
        .thenReturn(List.of(new OptionDictionaryProjection("prop_sku-type", "chair", "新名", 1)));
    TestTransactionManager testTransactionManager = new TestTransactionManager();
    DictionaryCache dictionaryCache =
        new DictionaryCache(optionDictionaryMapper, testTransactionManager);
    dictionaryCache.initialize();
    TransactionTemplate transactionTemplate = new TransactionTemplate(testTransactionManager);
    transactionTemplate.executeWithoutResult(
        transactionStatus -> {
          Map<DictionaryGroup, Map<String, String>> dictionarySnapshot = dictionaryCache.snapshot();
          assertTrue(dictionaryCache.refresh());
          assertSame(dictionarySnapshot, dictionaryCache.snapshot());
          assertEquals(
              "旧名", dictionaryCache.snapshot().get(DictionaryGroup.PROP_SKU_TYPE).get("chair"));
        });
    assertEquals("新名", dictionaryCache.snapshot().get(DictionaryGroup.PROP_SKU_TYPE).get("chair"));
  }

  /** 仅运行Spring事务同步生命周期，不冒充真实数据库事务测试。 */
  private static class TestTransactionManager extends AbstractPlatformTransactionManager {
    /** 创建独立测试事务。 */
    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    /** 测试无需打开数据库连接。 */
    @Override
    protected void doBegin(Object transaction, TransactionDefinition transactionDefinition) {}

    /** 测试提交后由Spring执行同步回调。 */
    @Override
    protected void doCommit(DefaultTransactionStatus defaultTransactionStatus) {}

    /** 测试回滚后由Spring跳过提交回调。 */
    @Override
    protected void doRollback(DefaultTransactionStatus defaultTransactionStatus) {}
  }
}
