package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class D4PickupAddressHttpTest {
  /** 两个实际入口按各自隔离身份读取原订单；地址簿随后修改，旧单地址仍可重开。 */
  @Test
  @EnabledIfEnvironmentVariable(named = "MJ_D4_PICKUP_RENDER", matches = "1")
  void bothPickupPagesRenderFrozenAddressAfterReentry() throws Exception {
    D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    D3BQuoteTest d3BQuoteTest = setup.fixture();
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.prepareFreightPool();
      String order = setup.dispatch(d3BQuoteTest);
      d3BQuoteTest.call(
          "POST",
          "/api/me/rental-orders/" + order + "/receipt/confirm",
          view("expectedVersion", 0),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.call(
          "POST",
          "/api/me/addresses/" + d3BQuoteTest.address + "/update",
          view(
              "expectedVersion",
              0,
              "receiverName",
              "当前联系人",
              "phone",
              "13900000002",
              "regionText",
              "上海市 徐汇区",
              "detailAddress",
              "新地址路89号",
              "isDefault",
              true),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          200);
      assertEquals(
          "新地址路89号",
          d3BQuoteTest.call("GET", "/api/me/addresses/" + d3BQuoteTest.address, null, d3BQuoteTest.buyer, null, 200)
              .path("detailAddress")
              .asString());
      render(d3BQuoteTest, order, false);
      assertEquals(
          1,
          d3BQuoteTest.f.db.update(
              "UPDATE rental_order SET detail_address_snapshot=NULL WHERE id=? AND factory_id=?",
              order,
              d3BQuoteTest.factoryId));
      render(d3BQuoteTest, order, true);
    } finally {
      setup.cleanup(d3BQuoteTest);
    }
  }

  /** 隔离会话仅通过子进程标准输入传入，截图和日志不包含登录凭据。 */
  void render(D3BQuoteTest d3BQuoteTest, String order, boolean missing) throws Exception {
    Path out = Path.of(System.getenv("MJ_D4_RENDER_OUT")).toAbsolutePath();
    Process process =
        new ProcessBuilder(
                System.getenv("MJ_D4_NODE"), "../mujian-frontend/tests/d4-pickup-render.cjs")
            .redirectErrorStream(true)
            .redirectOutput(
                out.resolve(missing ? "pickup-missing.log" : "pickup-frozen.log").toFile())
            .start();
    try (OutputStream stream = process.getOutputStream()) {
      stream.write(
          d3BQuoteTest.f.json.writeValueAsBytes(
              view(
                  "base",
                  d3BQuoteTest.f.base,
                  "order",
                  order,
                  "buyer",
                  d3BQuoteTest.buyer.token(),
                  "supplier",
                  d3BQuoteTest.supplier.token(),
                  "missing",
                  missing,
                  "output",
                  out.toString())));
    }
    try {
      assertTrue(process.waitFor(50, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }
}
