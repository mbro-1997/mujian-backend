package com.mujian.backend.casework.mapper;

import com.mujian.backend.common.enums.CaseContactChannel;

import com.mujian.backend.casework.dto.CaseContact;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminCaseNoteMapper {
  record CaseState(String id, String status, int version) {}
  record Note(String id, CaseContactChannel channel, String summary, LocalDateTime at, String snapshot) {}

  /** 锁住来自实际履约风险的事项，隔离其他模块的案件。 */
  CaseState lockContactCaseById(@Param("caseId") String caseId);

  /** 读取本事项可引用的全部来源和受影响订单。 */
  List<CaseContact.Reference> selectReferencesByCaseId(@Param("caseId") String caseId);

  /** 按本人、本事项、原请求号读取首次成功审计。 */
  String selectSnapshotByRequest(@Param("caseId") String caseId, @Param("actor") String actor,
      @Param("key") String key);

  /** 按业务时间读取本案联系历史与对应审计。 */
  List<Note> selectNotesByCaseId(@Param("caseId") String caseId);

  /** 将实际操作者、渠道和沟通结果写入原事项。 */
  void insertContactNote(@Param("id") String id, @Param("caseId") String caseId,
      @Param("input") CaseContact.Input input, @Param("actor") String actor, @Param("at") LocalDateTime at);

  /** 成功追加后递增版本，保留原事项状态。 */
  int incrementCaseVersion(@Param("caseId") String caseId, @Param("version") int version, @Param("at") LocalDateTime at);
}
