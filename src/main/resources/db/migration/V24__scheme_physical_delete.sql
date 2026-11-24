-- 历史来源 ID 和导出事实保留。来源清单的类型/非空约束、导出计次及保留期限不变。
ALTER TABLE rental_checklist DROP FOREIGN KEY fk_d3a_5_2;
ALTER TABLE scheme_export DROP FOREIGN KEY fk_d3a_4_1;

ALTER TABLE planning_operation_record
  DROP CHECK ck_d3a_8_2,
  ADD CONSTRAINT ck_d3a_8_2 CHECK (COALESCE((operation_code IN ('createScheme','updateScheme','archiveScheme','deleteScheme','setSchemeItem','removeSchemeItem','reorderSchemeItems','setSchemeShare','disableSchemeShare','exportSchemePdf','createChecklist','activateChecklist','setChecklistItem','updateChecklistItem','saveChecklist','removeChecklistItem','deleteChecklist','createAddress','updateAddress','setDefaultAddress','deleteAddress')),FALSE)=TRUE);
