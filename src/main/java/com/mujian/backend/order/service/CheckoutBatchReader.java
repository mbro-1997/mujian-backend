package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.payment.mapper.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

/** 按调用者归属查询合并付款批次。 */
public interface CheckoutBatchReader {
  /** 先鉴权后读取相关支付和退款，避免通过金额、渠道字段探测他人批次。 */
  BatchViews.Batch read(String actor, String batchId);
}
