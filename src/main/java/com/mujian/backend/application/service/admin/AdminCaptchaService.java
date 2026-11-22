package com.mujian.backend.application.service.admin;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.D2RateLimiter;
import com.mujian.backend.common.support.Ids;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class AdminCaptchaService {
  public record Challenge(String captchaId, String imageData, String expiresAt) {}

  private static final String LETTERS = "2346789ABCDEFGHJKLMNPQRTUVWXYZ";
  private static final DefaultRedisScript<Long> CONSUME =
      new DefaultRedisScript<>(
          """
          local value=redis.call('GET',KEYS[1])
          if not value or string.sub(value,1,32)~=ARGV[1] then return -1 end
          redis.call('DEL',KEYS[1])
          if string.sub(value,34)==ARGV[2] then return 1 end
          return 0
          """,
          Long.class);
  private final StringRedisTemplate redis;
  private final D2RateLimiter rateLimiter;
  private final Clock clock;
  private final String prefix;
  private final SecureRandom random = new SecureRandom();

  /** 隔离环境命名空间，沿用现有 Redis 限流。 */
  public AdminCaptchaService(
      StringRedisTemplate redis,
      D2RateLimiter rateLimiter,
      Clock clock,
      @Value("${mj.environment}") String environment) {
    this.redis = redis;
    this.rateLimiter = rateLimiter;
    this.clock = clock;
    prefix = "mj:" + environment + ":admin-captcha:";
  }

  /** 刷新本会话唯一挑战。图片为服务端光栅图，不嵌答案或生成种子。 */
  public Challenge create(String session, String ip) {
    rateLimiter.check("admin-captcha", ip, 30);
    String id = Ids.next();
    StringBuilder answer = new StringBuilder();
    BufferedImage image = new BufferedImage(164, 54, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(new Color(239, 243, 239));
      graphics.fillRect(0, 0, 164, 54);
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setColor(new Color(177, 190, 181));
      for (int i = 0; i < 9; i++)
        graphics.drawLine(
            random.nextInt(164), random.nextInt(54), random.nextInt(164), random.nextInt(54));
      for (int i = 0; i < 5; i++) {
        char letter = LETTERS.charAt(random.nextInt(LETTERS.length()));
        answer.append(letter);
        int x = 9 + i * 30, y = 34 + random.nextInt(9);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30 + random.nextInt(6)));
        graphics.setColor(
            new Color(28 + random.nextInt(45), 34 + random.nextInt(45), 40 + random.nextInt(45)));
        double rotation = (random.nextDouble() - .5) * .32;
        graphics.rotate(rotation, x, y);
        graphics.drawString(String.valueOf(letter), x, y);
        graphics.rotate(-rotation, x, y);
      }
    } finally {
      graphics.dispose();
    }
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "png", png);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
    redis.opsForValue().set(prefix + session, id + ":" + answer, Duration.ofMinutes(2));
    return new Challenge(
        id,
        "data:image/png;base64," + Base64.getEncoder().encodeToString(png.toByteArray()),
        time(now(clock).plusMinutes(2)));
  }

  /** 同会话、同编号才能消费。答错、答对均不能重放。 */
  public void consume(String session, String id, String answer) {
    Long result =
        redis.execute(
            CONSUME, List.of(prefix + session), id, answer.trim().toUpperCase(Locale.ROOT));
    if (result == null) throw ApiFailure.redis();
    if (result != 1)
      throw new ApiFailure(
          400, D2Codes.CODE_INVALID_ARGUMENT, result < 0 ? "CAPTCHA_EXPIRED" : "CAPTCHA_INVALID");
  }
}
