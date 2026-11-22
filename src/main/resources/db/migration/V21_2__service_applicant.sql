ALTER TABLE customer_service_session
  DROP CHECK customer_service_session_chk_3,
  ADD CONSTRAINT ck_service_session_role_factory CHECK (
    (active_role IN ('buyer','applicant') AND factory_id IS NULL)
    OR (active_role='factory' AND factory_id IS NOT NULL)
  );

ALTER TABLE customer_service_context
  DROP CHECK customer_service_context_chk_1,
  ADD CONSTRAINT ck_service_context_role_source CHECK (
    active_role IN ('buyer','factory')
    OR (active_role='applicant' AND source_type='factory_onboarding')
  ),
  ADD INDEX idx_service_context_owner_source (user_id,source_type,source_id,created_at,id);
