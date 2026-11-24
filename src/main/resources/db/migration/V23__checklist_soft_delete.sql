-- 整张清单逻辑删除：只释放根名额，不删除明细或变更历史支付与订单。
ALTER TABLE rental_checklist
  DROP CHECK ck_d3a_5_1,
  DROP CHECK ck_d3a_5_2,
  ADD CONSTRAINT ck_d3a_5_1 CHECK (COALESCE((checklist_status IN ('selecting','suspended','checkout_processing','converted_to_order','deleted')),FALSE)=TRUE),
  ADD CONSTRAINT ck_d3a_5_2 CHECK (COALESCE(((checklist_status IN ('selecting','suspended','checkout_processing') AND active_slot_no BETWEEN 1 AND 3) OR (checklist_status IN ('converted_to_order','deleted') AND active_slot_no IS NULL)),FALSE)=TRUE),
  ADD CONSTRAINT ck_checklist_deleted CHECK (checklist_status<>'deleted' OR (current_checkout_batch_id IS NULL AND converted_order_at IS NULL));

ALTER TABLE planning_operation_record
  DROP CHECK ck_d3a_8_2,
  ADD CONSTRAINT ck_d3a_8_2 CHECK (COALESCE((operation_code IN ('createScheme','updateScheme','archiveScheme','setSchemeItem','removeSchemeItem','reorderSchemeItems','setSchemeShare','disableSchemeShare','exportSchemePdf','createChecklist','activateChecklist','setChecklistItem','updateChecklistItem','saveChecklist','removeChecklistItem','deleteChecklist','createAddress','updateAddress','setDefaultAddress','deleteAddress')),FALSE)=TRUE);
