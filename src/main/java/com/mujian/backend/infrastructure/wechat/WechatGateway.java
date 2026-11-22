package com.mujian.backend.infrastructure.wechat;

import com.mujian.backend.common.enums.BusinessFailureReason;

public interface WechatGateway {
 record Identity(String appId,String openid,String unionid,String phone,String countryCode){}
 /** 为指定场景生成小程序码。 */
 default byte[] miniCode(String scene,String page,String environment){throw com.mujian.backend.common.support.D2Values.unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name());}
 /** 使用登录凭据换取微信身份。 */
 Identity exchange(String loginCode,String phoneCode);
}
