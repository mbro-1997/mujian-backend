-- C-CROSS-01: preserve V9 checksum; only correct the internal prebinding state.
ALTER TABLE prop_qr_code DROP CHECK ck_c12_12;
UPDATE prop_qr_code SET status='unused' WHERE status='unbound';
ALTER TABLE prop_qr_code ADD CONSTRAINT ck_c12_12 CHECK (COALESCE((status IS NULL OR status IN ('unused','bound','revoked')),FALSE) = TRUE);
