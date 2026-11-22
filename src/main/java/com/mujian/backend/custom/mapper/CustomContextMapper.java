package com.mujian.backend.custom.mapper;

import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.payment.domain.RefundOrderDO;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CustomContextMapper {
  record Factory(String id, String name, String mainBusiness) {}

  record Statement(String id, String summary, String evidence, String at) {}

  /** 按 sale 查询 custom_after_sale_factory_statement 记录。 */
  List<Statement> selectFactoryStatementsByAfterSale(@Param("sale") String sale);

  /** 按 id, sale, actor 新增 custom_after_sale_factory_statement 记录。 */
  int insertCustomAfterSaleFactoryStatement(
      @Param("id") String id,
      @Param("sale") String sale,
      @Param("actor") String actor,
      @Param("summary") String summary,
      @Param("evidence") String evidence,
      @Param("at") java.time.LocalDateTime at);

  /** 按 sale, at 更新 custom_after_sale_request 记录。 */
  int advanceSale(@Param("sale") String sale, @Param("at") java.time.LocalDateTime at);

  record Note(String id, String channel, String summary, String at) {}

  record Case(String id, int version, String state) {}

  /** 按 page 查询 factory_profile 记录。 */
  List<Factory> selectCustomContextFactories(@Param("page") PageQuery page);

  /** 查询 factory_profile 记录。 */
  long factoryCount();

  /** 按 id 查询 factory_profile 记录。 */
  Factory selectCustomContextFactory(@Param("id") String id);

  /** 按 id 查询 admin_case 记录。 */
  Case caseById(@Param("id") String id);

  /** 按 id 查询 admin_case_note 记录。 */
  List<Note> selectNotesByCase(@Param("id") String id);

  /** 按 order 查询 refund_order 记录。 */
  List<RefundOrderDO> selectCustomRefundsByOrderId(@Param("order") String order);
}
