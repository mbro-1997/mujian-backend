package com.mujian.backend.d2a;

import com.mujian.backend.material.service.MaterialCleanupAudit;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.storage.CosMaterialStore;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.Stored;
import com.mujian.backend.infrastructure.wechat.OfficialWechatGateway;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.core.env.*;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Explicit test-classpath probe. Only C-authorized private prefixes; no DB/ACL/delete/publish. */
public final class D2CRealChannelProbeCli {
 /** 启动本测试命令行入口。 */
 public static void main(String[] args)throws Exception{
  ObjectMapper json=new ObjectMapper();boolean qrOnly=args.length>0&&args[0].startsWith("qr");Path state=Path.of(qrOnly?".local/test/d2c-real-"+args[0]+".json":".local/test/d2c-real-channel.json");LinkedHashMap<String, Object> report=new LinkedHashMap<String,Object>();report.put("time",Instant.now().toString());report.put("realChannel",true);report.put("deleteExecuted",false);report.put("phoneScanVerified",false);
  for(String logger:List.of("com.qcloud.cos","org.apache.http"))((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(logger)).setLevel(ch.qos.logback.classic.Level.OFF);
  if(java.util.Arrays.asList(args).contains("expiry")){JsonNode data=json.readTree(Files.readString(state));if(Instant.now().isBefore(Instant.parse(data.path("expiresAt").asString()).plusSeconds(5)))throw new IllegalStateException("Not expired");ArrayList<Object> results=new ArrayList<Object>();for(JsonNode sample:data.path("samples"))results.add(Map.of("prefix",sample.path("prefix").asString(),"expiredHttp",D2RealChannelProbeCli.get(sample.path("signedUrl").asString())));report.put("samples",results);
  }else{
   if(Files.exists(state))throw new IllegalStateException("Existing probe; do not overwrite or re-upload");
   Path path=Path.of(".local/runtime.properties");if(!Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString("rw-------")))throw new IllegalStateException("Config must be 600");Properties p=new Properties();try(InputStream in=Files.newInputStream(path)){p.load(in);}StandardEnvironment env=new StandardEnvironment();env.getPropertySources().addFirst(new PropertiesPropertySource("protected-runtime",p));CosMaterialStore store=new CosMaterialStore(env);ArrayList<Object> samples=new ArrayList<Object>();ArrayList<Object> secrets=new ArrayList<Object>();Instant expires=Instant.now().plusSeconds(120);
   try{byte[] qr=null;try{OfficialWechatGateway gateway=new OfficialWechatGateway(p.getProperty("mj.wechat.app-id"),p.getProperty("mj.wechat.app-secret"),json,null);Field field=OfficialWechatGateway.class.getDeclaredField("qrCheckPath");field.setAccessible(true);field.set(gateway,false);qr=gateway.miniCode(Ids.next(),"pages/props/scan/index","develop");BufferedImage im=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(qr));report.put("wechat",Map.of("state","generated","width",im.getWidth(),"height",im.getHeight(),"envVersion","develop","checkPath",false,"boundary","generation only; fictional unbound scene; unpublished page"));Files.write(Path.of(System.getenv("MJ_EVIDENCE_DIR"),"real-wechat-generation.png"),qr);}catch(Exception e){report.put("wechat",Map.of("state","failed","errorType",e.getClass().getSimpleName(),"reason",e instanceof com.mujian.backend.common.api.ApiFailure a?a.reason:"UNAVAILABLE"));}
   for(String prefix:qrOnly?List.of("dev/d2-c/qr-images/"):List.of("dev/d2-c/props/","dev/d2-c/qr-evidence/","dev/d2-c/qr-images/")){if(prefix.endsWith("qr-images/")&&qr==null)continue;String key=prefix+Ids.next()+"/"+(prefix.endsWith("qr-images/")?java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(qr))+".png":Ids.next());byte[] bytes=prefix.endsWith("qr-images/")?qr:D2ApplicationTest.png();Map<String, String> meta=Map.of("purpose","c-private-channel-probe","accepted-at",Instant.now().toString());LinkedHashMap<String, Object> row=new LinkedHashMap<String,Object>();row.put("prefix",prefix);row.put("keyHash",MaterialCleanupAudit.digest(key));try{store.put(key,bytes,"image/png",meta);Stored head=store.head(key);String signed=store.sign(key,expires);row.put("metadataMatches",head.metadata().entrySet().containsAll(meta.entrySet()));row.put("signedHttp",D2RealChannelProbeCli.get(signed));row.put("anonymousHttp",D2RealChannelProbeCli.get("https://cct1-1429809468.cos.ap-shanghai.myqcloud.com/"+key));secrets.add(Map.of("prefix",prefix,"key",key,"signedUrl",signed));}catch(Exception e){row.put("state","failed");row.put("errorType",e.getClass().getSimpleName());}samples.add(row);}report.put("samples",samples);report.put("deleteGate",store.deletionPolicyVerified());report.put("expiresAt",expires.toString());Files.createFile(state,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));Files.writeString(state,json.writeValueAsString(Map.of("samples",secrets,"expiresAt",expires.toString())));
   }finally{store.close();}
  }
  System.out.println(json.writeValueAsString(report));
 }
}
