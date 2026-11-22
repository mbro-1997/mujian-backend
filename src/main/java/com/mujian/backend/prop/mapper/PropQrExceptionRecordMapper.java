package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropQrExceptionRecordDO;
import com.mujian.backend.prop.dto.PropWriteDTO;
import com.mujian.backend.prop.dto.QrExceptionQueryDTO;
import com.mujian.backend.prop.projection.PropQrExceptionProjection;
import com.mujian.backend.prop.projection.PropQrExceptionStatsProjection;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropQrExceptionRecordMapper {

  /** 保存二维码异常处理结果和替换码关联。 */
  int updateQrExceptionResolution(
      @Param("command") PropWriteDTO.ResolveQrException resolveQrException);

  /** 保存二维码异常记录。 */
  void insertPropQrExceptionRecord(@Param("row") PropQrExceptionRecordDO propQrExceptionRecordDO);

  /** 读取尚待补齐工单关联的二维码异常记录。 */
  List<PropQrExceptionRecordDO> selectUnlinkedPendingPropQrExceptionRecordList();

  /** 按用户与请求编号恢复二维码异常记录。 */
  List<PropQrExceptionRecordDO> selectPropQrExceptionRecordListByActorAndRequest(
      @Param("actorId") String actorId, @Param("requestId") String requestId);

  /** 按记录标识读取二维码异常记录。 */
  PropQrExceptionRecordDO selectPropQrExceptionRecord(
      @Param("id") String id, @Param("lock") boolean lock);

  /** 查找二维码尚未处理的异常申请。 */
  List<PropQrExceptionRecordDO> selectPendingQrExceptionListByQrId(@Param("qrId") String qrId);

  /** 读取二维码异常详情及关联道具信息。 */
  List<PropQrExceptionProjection> selectQrExceptionDetailById(@Param("id") String id);

  /** 统计二维码异常各处理分类的数量。 */
  List<PropQrExceptionStatsProjection> selectQrExceptionStatsByQuery(
      @Param("qrExceptionQueryDTO") QrExceptionQueryDTO qrExceptionQueryDTO);

  /** 统计匹配的二维码异常数量供分页。 */
  long selectQrExceptionCountByQuery(@Param("qrExceptionQueryDTO") QrExceptionQueryDTO qrExceptionQueryDTO);

  /** 按筛选和分页读取二维码异常列表。 */
  List<PropQrExceptionProjection> selectQrExceptionPageByQuery(
      @Param("qrExceptionQueryDTO") QrExceptionQueryDTO qrExceptionQueryDTO);
}
