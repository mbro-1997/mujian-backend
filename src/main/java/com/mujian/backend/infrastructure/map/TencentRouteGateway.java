package com.mujian.backend.infrastructure.map;

import static com.mujian.backend.common.support.D2Values.unavailable;

import com.mujian.backend.common.enums.*;
import java.math.BigDecimal;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;

/** 腾讯地图适配；有限超时、一次读取重试与五分钟有向路线缓存。 */
@Component
public class TencentRouteGateway implements RouteGateway {
  private record CachedPoint(Point point, Instant expires) {}

  private record RouteKey(Point from, Point to) {}

  private final Environment environment;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final HttpClient httpClient =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private final ConcurrentHashMap<String, CachedPoint> points = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<RouteKey, Route> routes = new ConcurrentHashMap<>();

  /** 可选配置缺失只限制地图调用，不阻止其他业务启动。 */
  public TencentRouteGateway(Environment environment, ObjectMapper objectMapper, Clock clock) {
    this.environment = environment;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 地址解析明确返回行政编码，不从中文地址反向拆分。 */
  public Point geocode(String address) {
    CachedPoint cachedPointCached = points.get(address);
    if (cachedPointCached != null && clock.instant().isBefore(cachedPointCached.expires()))
      return cachedPointCached.point();
    JsonNode result = read("/ws/geocoder/v1/?address=" + encode(address));
    JsonNode location = result.path("location"), ad = result.path("ad_info");
    String latitude = coordinate(location.get("lat"), 90),
        longitude = coordinate(location.get("lng"), 180);
    String region = ad.path("adcode").asString(), city = ad.path("city_code").asString();
    if (!region.matches("[0-9]{6}")) throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
    // 普通正向解析只保证adcode；需要市编码时按同一坐标查官方逆解析，不拆中文地址。
    if (!city.matches("[0-9]{6}")) {
      JsonNode reverse =
          read("/ws/geocoder/v1/?location=" + encode(latitude + "," + longitude) + "&get_poi=0");
      city = reverse.path("ad_info").path("city_code").asString();
      if (!city.matches("[0-9]{6}")) throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
    }
    Point point = new Point(latitude, longitude, city, region);
    if (points.size() > 512) points.clear();
    points.put(address, new CachedPoint(point, clock.instant().plusSeconds(300)));
    return point;
  }

  /** 去回程使用不同键，不把反向距离复用为正向距离。 */
  public Route driving(Point pointFrom, Point pointTo) {
    RouteKey routeKeyKey = new RouteKey(pointFrom, pointTo);
    Route routeCached = routes.get(routeKeyKey);
    if (routeCached != null
        && clock.instant().isBefore(routeCached.calculatedAt().plusSeconds(300)))
      return routeCached;
    JsonNode result =
        read(
            "/ws/direction/v1/driving/?from="
                + encode(pointFrom.latitude() + "," + pointFrom.longitude())
                + "&to="
                + encode(pointTo.latitude() + "," + pointTo.longitude())
                + "&policy=LEAST_TIME&no_step=1");
    JsonNode choices = result.path("routes");
    if (!choices.isArray() || choices.isEmpty())
      throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
    JsonNode distance = choices.get(0).path("distance");
    if (!distance.isIntegralNumber() || !distance.canConvertToInt() || distance.asInt() < 0)
      throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
    Route route = new Route(pointFrom, pointTo, distance.asInt(), clock.instant());
    if (routes.size() > 512) routes.clear();
    routes.put(routeKeyKey, route);
    return route;
  }

  /** 限定官方HTTPS主机，读取异常最多重试一次，不记录含密钥的URL。 */
  private JsonNode read(String path) {
    String key = environment.getProperty("mj.map.tencent.key", "");
    if (key.isBlank()) throw unavailable(TradingReason.MAP_CHANNEL_NOT_READY.name());
    URI uri = URI.create("https://apis.map.qq.com" + path + "&key=" + encode(key));
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        HttpRequest request =
            HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<byte[]> response =
            httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200 || response.body().length > 1024 * 1024)
          throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
        JsonNode body = objectMapper.readTree(response.body());
        if (body.path("status").asInt(-1) != 0)
          throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
        return body.path("result");
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
      } catch (java.io.IOException exception) {
        if (attempt == 1) throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
      }
    }
    throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
  }

  /** 坐标使用精确十进制字符串并限制地理范围。 */
  private String coordinate(JsonNode value, int limit) {
    if (value == null || !value.isNumber()) throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
    BigDecimal number = value.decimalValue();
    if (number.abs().compareTo(BigDecimal.valueOf(limit)) > 0)
      throw unavailable(TradingReason.MAP_UNAVAILABLE.name());
    return number.toPlainString();
  }

  /** 编码外部请求参数，不拼接原始地址。 */
  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
