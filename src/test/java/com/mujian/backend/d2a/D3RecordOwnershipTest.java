package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.address.dto.AddressDtos.*;
import com.mujian.backend.address.vo.AddressViews.Address;
import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.planning.vo.PlanningViews.Prices;
import com.mujian.backend.scheme.dto.SchemeDtos.*;
import com.mujian.backend.scheme.vo.SchemeViews.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 契约修订后的record线级字段与可空/隐藏口径验证。 */
class D3RecordOwnershipTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 方案动作请求、精简PDF请求及未变地址标志可按契约序列化。 */
  @Test
  void schemeAndAddressRequestsKeepTheirWireShape() {
    String scheme =
        """
        {"actions":[]}
        """;
    UpdateScheme updateScheme = objectMapper.readValue(scheme, UpdateScheme.class);
    assertEquals(objectMapper.readTree(scheme), objectMapper.valueToTree(updateScheme));
    String address =
        """
        {"expectedVersion":1,"receiverName":"虚构联系人","phone":"13800000000",
         "regionText":"测试区","detailAddress":"测试地址","isDefault":true}
        """;
    UpdateAddress updateAddress = objectMapper.readValue(address, UpdateAddress.class);
    assertEquals(objectMapper.readTree(address), objectMapper.valueToTree(updateAddress));
    assertTrue(updateAddress.isDefault());
    assertEquals(
        objectMapper.readTree("{\"hidePrice\":true}"),
        objectMapper.valueToTree(new ExportPdf(true)));
  }

  /** 隐藏分享通过实际ApiResponse包装序列化也没有价格字段；空字段与地址默认命名不变。 */
  @Test
  void sharingNullsAndDefaultAddressKeepTheirWireShape() {
    SharedScheme hidden = new HiddenScheme("方案", null, "editing", 2, "0", "仅参考", true, List.of());
    JsonNode hiddenJson =
        objectMapper
            .valueToTree(new ApiResponse<SharedScheme>("OK", "成功", hidden, "trace"))
            .path("data");
    assertEquals(
        objectMapper.readTree(
            """
            {"name":"方案","description":null,"status":"editing","contentVersion":2,
             "quantityTotal":"0","disclaimer":"仅参考","hidePrice":true,"items":[]}
            """),
        hiddenJson);
    SharedScheme priced =
        new PricedScheme("方案", null, "editing", 2, "0", "仅参考", false, List.of(), 100, 200);
    JsonNode pricedJson =
        objectMapper
            .valueToTree(new ApiResponse<SharedScheme>("OK", "成功", priced, "trace"))
            .path("data");
    assertEquals(100, pricedJson.path("estimatedDailyRentFen").asInt());
    assertEquals(200, pricedJson.path("estimatedDepositFen").asInt());
    JsonNode addressJson =
        objectMapper.valueToTree(
            new Address(
                "id", "虚构联系人", "13800000000", "测试区", "测试地址", true, 1, "created", "updated"));
    assertTrue(addressJson.path("isDefault").asBoolean());
    assertFalse(addressJson.has("default"));
    assertEquals(
        objectMapper.readTree("{\"dailyRentPriceFen\":100,\"depositAmountFen\":200}"),
        objectMapper.valueToTree(new Prices(100, 200)));
  }
}
