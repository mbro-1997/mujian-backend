package com.mujian.backend.prop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.prop.service.impl.PropMaterialServiceImpl;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PropPublicImageTest {
  /** 固定时钟验证公开图30分钟、UTF8哈希和私有图仍120秒。 */
  @Test
  void publicImageHashAndExpiryKeepPrivatePolicySeparate() {
    PrivateMaterialStore privateMaterialStore = mock(PrivateMaterialStore.class);
    Instant now = Instant.parse("2026-11-05T12:00:00.123Z");
    String key = "dev/d2-c/props/中文/图片.png";
    when(privateMaterialStore.sign(eq(key), any())).thenReturn("https://example.invalid/signed");
    PropMaterialServiceImpl propMaterialServiceImpl =
        new PropMaterialServiceImpl(
            null,
            null,
            null,
            null,
            privateMaterialStore,
            null,
            Clock.fixed(now, ZoneOffset.UTC),
            null);
    PublicImageViewVO publicImageViewVO = propMaterialServiceImpl.publicImage(key);
    assertEquals(
        "f62e7f7e10967d7bd48d6c10766a0e93f0a94ae90013c2856f870fcbfb6e7bf4",
        publicImageViewVO.getCacheKey());
    assertEquals("2026-11-05T20:30:00.000+08:00", publicImageViewVO.getExpiresAt());
    verify(privateMaterialStore).sign(key, now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1800));
    assertEquals("2026-11-05T20:02:00.123+08:00", propMaterialServiceImpl.sign(key).getExpiresAt());
    verify(privateMaterialStore).sign(key, now.plusSeconds(120));
    assertNull(propMaterialServiceImpl.publicImage(null));
  }
}
