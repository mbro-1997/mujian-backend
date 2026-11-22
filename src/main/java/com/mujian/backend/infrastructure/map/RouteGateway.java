package com.mujian.backend.infrastructure.map;

import java.time.Instant;

/** 地图只提供可信坐标和路线，不接受客户端权威距离。 */
public interface RouteGateway {
  record Point(String latitude, String longitude, String cityCode, String regionCode) {}

  record Route(Point from, Point to, int distanceMeter, Instant calculatedAt) {}

  /** 查询完整地址的GCJ02位置与行政编码。 */
  Point geocode(String address);

  /** 按LEAST_TIME分别查询有方向的去程或回程。 */
  Route driving(Point pointFrom, Point pointTo);
}
