-- 外部运单号保留原始字符，每项仍限制为 128 字符。
ALTER TABLE order_return_shipment
  MODIFY COLUMN tracking_no VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL DEFAULT NULL COMMENT '物流/运输凭证号';

ALTER TABLE order_transport_expense_statement
  MODIFY COLUMN outbound_carrier_order_no VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL DEFAULT NULL COMMENT '去程第三方订单号',
  MODIFY COLUMN return_carrier_order_no VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NULL DEFAULT NULL COMMENT '回程第三方订单号';
