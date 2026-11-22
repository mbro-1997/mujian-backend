package com.mujian.backend.d2a;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.material.service.*;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import com.mujian.backend.material.mapper.MaterialReferenceMapper;
import com.mujian.backend.infrastructure.storage.CosMaterialStore;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.Stored;
import com.mujian.backend.support.TestDatabase;
import com.qcloud.cos.*;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.region.Region;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Explicit test-classpath upload preparation. Never runs cleanup or lists an entire bucket. */
public final class D2RemainingCloudCli {
  private static final String OWNER="01d41c95c952417da6817ec53cd8ecf7";
  private static final String DATABASE="darkmujian_d1_test_d2a_20261028185351_4ebca4";
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    if(args.length!=1 || !args[0].equals("prepare"))throw new IllegalArgumentException("prepare only; no delete capability");
    Path state=Path.of(".local/test/remaining-cloud-samples.json");
    if(Files.exists(state))throw new IllegalStateException("Existing sample registration: reuse, never overwrite or re-upload");
    Properties configured=new Properties();
    try(InputStream in=Files.newInputStream(Path.of(System.getenv("MJ_TEST_CONFIG")))){configured.load(in);}
    if(!configured.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql://127.0.0.1:3306/"+DATABASE+"?"))throw new IllegalStateException("original isolated database required");
    Properties test=TestDatabase.prepare();
    Properties runtime=new Properties(); Path secrets=Path.of(".local/runtime.properties");
    if(!Files.getPosixFilePermissions(secrets).equals(PosixFilePermissions.fromString("rw-------")))throw new IllegalStateException("runtime must be 600");
    try(InputStream in=Files.newInputStream(secrets)){runtime.load(in);}
    for(String k:List.of("mj.cos.secret-id","mj.cos.secret-key")){
      String v=runtime.getProperty(k);if(v==null||v.isBlank())throw new IllegalStateException("missing local COS credential");test.setProperty(k,v);
    }
    test.setProperty("mj.environment","test");test.setProperty("server.port","0");test.setProperty("server.address","127.0.0.1");
    test.setProperty("mj.verification.cleanup.enabled","false");test.setProperty("mj.verification.cleanup.cloud-policy-verified","false");test.setProperty("mj.prop.qr-worker-enabled","false");
    SpringApplication application=new SpringApplication(MujianBackendApplication.class);
    application.addInitializers(c->c.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("isolated-only",test)));
    ObjectMapper json=new ObjectMapper();LinkedHashMap<String, Object> report=new LinkedHashMap<String,Object>();
    Path out=Path.of(System.getenv("MJ_EVIDENCE_DIR"),"c121012-cloud-preparation.json");
    ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("com.qcloud.cos")).setLevel(ch.qos.logback.classic.Level.OFF);
    try(ConfigurableApplicationContext app=application.run()) {
      JdbcTemplate db=app.getBean(JdbcTemplate.class);
      if(!DATABASE.equals(db.queryForObject("SELECT DATABASE()",String.class)))throw new IllegalStateException("database mismatch");
      if(db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id=? AND user_status='active' AND register_status='demander_bound'",Integer.class,OWNER)!=1)throw new IllegalStateException("existing owner unavailable");
      MaterialReferenceMapper refs=app.getBean(MaterialReferenceMapper.class);CosMaterialStore store=app.getBean(CosMaterialStore.class);
      ClientConfig cfg=new ClientConfig(new Region("ap-shanghai"));cfg.setHttpProtocol(HttpProtocol.https);cfg.setMaxErrorRetry(0);cfg.setConnectionTimeout(4000);cfg.setSocketTimeout(15000);
      COSClient client=new COSClient(new BasicCOSCredentials(test.getProperty("mj.cos.secret-id"),test.getProperty("mj.cos.secret-key")),cfg);
      ArrayList<Map<String, Object>> samples=new ArrayList<Map<String,Object>>();ArrayList<Map<String, Object>> publicSamples=new ArrayList<Map<String,Object>>();
      report.put("database",DATABASE);report.put("owner",OWNER);report.put("ownerPreexisting",true);report.put("time",Instant.now().toString());
      try {
        ListObjectsRequest query=new ListObjectsRequest();query.setBucketName("cct1-1429809468");query.setPrefix(CosMaterialStore.PREFIX+"student/"+OWNER+"/");query.setMaxKeys(20);
        ObjectListing found=client.listObjects(query);report.put("targetedOwnerObjects",found.getObjectSummaries().size());report.put("listingTruncated",found.isTruncated());
        if(found.isTruncated())throw new IllegalStateException("bounded selection incomplete; no broad scan");
        for(COSObjectSummary item:found.getObjectSummaries()) {
          Stored object=store.head(item.getKey());
          if(!Instant.now().isBefore(object.lastModified().plus(Duration.ofHours(48))) && refs.countMaterialReferencesByOwnerAndKey(OWNER,object.key())==0 && OWNER.equals(object.metadata().get("owner")) && "student_card".equals(object.metadata().get("purpose"))) {
            LinkedHashMap<String, Object> row=new LinkedHashMap<String,Object>();row.put("key",object.key());row.put("createdAt",object.lastModified().toString());row.put("eligibleAt",object.lastModified().plus(Duration.ofHours(48)).toString());row.put("source","existing");samples.add(row);
            if(samples.size()==2)break;
          }
        }
        Files.createFile(state,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        // Persist every successful cloud upload immediately. A partial run is never blindly retried.
        Files.writeString(state,json.writeValueAsString(Map.of("owner",OWNER,"database",DATABASE,"samples",samples)));
        while(samples.size()<2) {
          Receipt receipt=app.getBean(VerificationMaterials.class).upload(OWNER,"student_card","student-material-v1",true,Files.readAllBytes(Path.of(".local/test/native-materials/front.png")));
          LinkedHashMap<String, Object> row=new LinkedHashMap<String,Object>();row.put("key",receipt.objectKey());row.put("source","normal VerificationMaterials.upload");samples.add(row);
          Files.writeString(state,json.writeValueAsString(Map.of("owner",OWNER,"database",DATABASE,"samples",samples)));
          Stored object=store.head(receipt.objectKey());row.put("createdAt",object.lastModified().toString());row.put("eligibleAt",object.lastModified().plus(Duration.ofHours(48)).toString());
          Files.writeString(state,json.writeValueAsString(Map.of("owner",OWNER,"database",DATABASE,"samples",samples)));
        }
        for(Map<String, Object> s:samples){String key=(String)s.get("key");int references=refs.countMaterialReferencesByOwnerAndKey(OWNER,key);if(references!=0)throw new IllegalStateException("sample gained reference; retain");LinkedHashMap<String, Object> row=new LinkedHashMap<>(s);row.remove("key");row.put("objectKeyHash",MaterialCleanupAudit.digest(key));row.put("referenceCount",references);publicSamples.add(row);}
        report.put("samples",publicSamples);report.put("deleted",false);report.put("normalServiceAndRealCOS",true);report.put("rawKeysInPrivateStateOnly",true);report.put("oldOrphanProbeUntouched",true);
      } finally {client.shutdown();}
    } catch(Exception e) {report.put("errorType",e.getClass().getSimpleName());throw new IllegalStateException("isolated cloud preparation failed: "+e.getClass().getSimpleName());} finally {
      Files.writeString(out,json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
    System.out.println("Prepared exact test samples; see redacted evidence, no deletion executed");
  }
}
