package com.mujian.backend.casework.dto;

import com.mujian.backend.common.enums.CaseContactChannel;

import java.util.List;

/** 管理事项只允许引用现有领域关联，联系记录与审计同事务保存。 */
public final class CaseContact {
  private CaseContact() {}

  public record Reference(String relationType, String relationId) {}
  public record Input(int caseVersion, CaseContactChannel contactChannel, String summary, List<Reference> references) {}
  public record Result(String noteId, String caseId, int caseVersion, String createdAt) {}
  public record Snapshot(String requestHash, Result result, List<Reference> references) {}
  public record Row(String id, CaseContactChannel channel, String summary, String createdAt, List<Reference> references) {}
}
