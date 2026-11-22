package com.mujian.backend.common.security;

import java.io.*;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.springframework.data.redis.serializer.*;

/** 会话只接受基本标量和框架 CSRF 对象；不允许任意业务对象反序列化。 */
public final class SessionValueSerializer implements RedisSerializer<Object> {
  /** 仅接受会话存储声明支持的值类型。 */
  private static boolean allowed(Class<?> c) {
    return c == java.util.HashMap.class
        || c == String.class
        || c == Long.class
        || c == Integer.class
        || c == Boolean.class
        || c == Number.class
        || c == org.springframework.security.web.csrf.DefaultCsrfToken.class;
  }

  /** 仅编码允许持久化的会话属性类型。 */
  public byte[] serialize(Object value) {
    if (value == null) return new byte[0];
    if (!allowed(value.getClass())) throw new SerializationException("不允许的会话类型");
    try (ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream stream = new ObjectOutputStream(out)) {
      stream.writeObject(value);
      return out.toByteArray();
    } catch (IOException e) {
      throw new SerializationException("会话编码失败", e);
    }
  }

  /** 校验类型后恢复会话属性。 */
  public Object deserialize(byte[] value) {
    if (value == null || value.length == 0) return null;
    if (value[0] != (byte) 0xac) {
      String scalar = new String(value, java.nio.charset.StandardCharsets.US_ASCII);
      if (scalar.matches("[0-9]{1,16}")) return Long.parseLong(scalar);
      throw new SerializationException("非法会话标量");
    }
    try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(value))) {
      in.setObjectInputFilter(
          info ->
              info.depth() > 8 || info.references() > 32 || info.streamBytes() > 4096
                  ? ObjectInputFilter.Status.REJECTED
                  : info.serialClass() == null
                      ? ObjectInputFilter.Status.UNDECIDED
                      : allowed(info.serialClass())
                          ? ObjectInputFilter.Status.ALLOWED
                          : ObjectInputFilter.Status.REJECTED);
      return in.readObject();
    } catch (IOException | ClassNotFoundException e) {
      throw new SerializationException("会话解码失败", e);
    }
  }
}
