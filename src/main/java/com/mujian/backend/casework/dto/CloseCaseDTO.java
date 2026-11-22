package com.mujian.backend.casework.dto;

import java.time.*;
import java.util.*;

/** 关闭工单的版本条件和结案结果。 */
public class CloseCaseDTO {
  private String id;
  private Integer version;
  private String status;
  private String summary;
  private String checklist;
  private LocalDateTime now;
  private String actor;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取version。 */
  public Integer getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(Integer version) {
    this.version = version;
  }

  /** 读取status。 */
  public String getStatus() {
    return status;
  }

  /** 设置status。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 读取summary。 */
  public String getSummary() {
    return summary;
  }

  /** 设置summary。 */
  public void setSummary(String summary) {
    this.summary = summary;
  }

  /** 读取checklist。 */
  public String getChecklist() {
    return checklist;
  }

  /** 设置checklist。 */
  public void setChecklist(String checklist) {
    this.checklist = checklist;
  }

  /** 读取now。 */
  public LocalDateTime getNow() {
    return now;
  }

  /** 设置now。 */
  public void setNow(LocalDateTime now) {
    this.now = now;
  }

  /** 读取actor。 */
  public String getActor() {
    return actor;
  }

  /** 设置actor。 */
  public void setActor(String actor) {
    this.actor = actor;
  }

  /** 组装id。 */
  public CloseCaseDTO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装version。 */
  public CloseCaseDTO version(Integer version) {
    this.version = version;
    return this;
  }

  /** 组装status。 */
  public CloseCaseDTO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装summary。 */
  public CloseCaseDTO summary(String summary) {
    this.summary = summary;
    return this;
  }

  /** 组装checklist。 */
  public CloseCaseDTO checklist(String checklist) {
    this.checklist = checklist;
    return this;
  }

  /** 组装now。 */
  public CloseCaseDTO now(LocalDateTime now) {
    this.now = now;
    return this;
  }

  /** 组装actor。 */
  public CloseCaseDTO actor(String actor) {
    this.actor = actor;
    return this;
  }
}
