# 幕间后端 · D1 + D2-A/B/C

**现行规范入口**：[新开发规范](../docs/新开发规范.md)统一目录、职责和编码规则，本仓库docs内同名文件仅为入口指引。根包迁移任务与当前状态见[03执行规范末尾](../../开发交接/03-Agent执行规范与验收清单.md)；2026-11-06已完成命名迁移及定向验证，根包`com.mujian.backend`、启动类`MujianBackendApplication`、项目groupId `com.mujian`。新产物位于`target/naming-migration/mujian-backend-0.1.0.jar`，共享运行JAR/进程未切换。D3-A可接续，不按下方历史记录重复派发已收口工作。

**Java类型规范（2026-11-05）**：整个后端（含公共层、测试和工具）禁止使用 `var`，变量声明必须写明类型并保留泛型。后续新增代码、示例及模板同样遵守。详见[统一执行规范](../../开发交接/03-Agent执行规范与验收清单.md#java-explicit-types-20261105)。

**业务SQL规范（2026-11-03）**：统一放入`src/main/resources/mapper/`对应XML，Mapper.java仅方法及必要参数/映射标识注解；禁止SQL注解、Provider及业务Java拼SQL。详见[工程规范](docs/D1-工程说明.md)末节；本次D2负责迁移，结果回填[原C-27](../../开发交接/D2-准入与道具可租/C-27-D2-C实施证据与剩余项.md#sql-xml-migration)，当前尚未完成。

## 2026-10-29 当前交付入口（C批）

当前两工程dev2，未提交/推送/发布。C批55接口、道具审核/库存/二维码/租期及OpenAPI 2026-10-29.5增量已落地；验收和剩余项以[U-13](../../开发交接/D2-准入与道具可租/U-13-D2-C实施验收摘要.md)、[C-27](../../开发交接/D2-准入与道具可租/C-27-D2-C实施证据与剩余项.md)为准，下方旧阶段数量/未验描述保留历史意义。

正式本机后端使用`target/d2c/mujian-backend-0.1.0.jar --spring.profiles.active=local`，18080；管理端5173默认代理18080。开发库darkmujian已V11，不能重复初始化。当前未在开发库创建C演示商品。隔离18081/18082/5174已关，配置与样本保留`.local/test/`且忽略。小程序恢复集中LAN开发地址，不在业务页注入账号。

本次最终12项后端增量、21项前端、34 WXML、管理端构建通过；55操作279响应经.5契约复校全部通过（原404遗漏已补齐）。真实微信生成与C私有COS访问/过期已实测，真机扫码与完整原生/视觉未签收，不能将构建或测试替身当真机通过。当前停在总工复核。

工作分支 dev2，未提交。实现范围及验收结果以 [C-02](../../开发交接/D1-最小框架/C-02-D1-01实施任务.md)、[C-05](../../开发交接/D1-最小框架/C-05-验证与交付记录.md) 为准。D1 保留7表/8操作；D2-A追加12表/23操作，当前共19表/31操作。已实现账号与两类认证、审核及私有材料适配器；真实微信/COS渠道尚未验收，订单不在本批范围。当前交付与未完成项见 [D2-A C-12](../../开发交接/D2-准入与道具可租/C-12-D2-A实施证据与剩余项.md)。

## 本机启动

2026-10-27：本地启动已改为 Spring Profiles。先保证 MySQL、Redis 在线，在后端根目录执行：

```sh
mvn spring-boot:run
```

同一工作区的小程序配置为局域网模式时，本地共享后端就绪后会检查并复用／启动手机测试账号服务，见[开发版测试账号](../../开发交接/切换身份脚本.md)。

Maven 默认启用 `local`，自动加载 `application.yml`、`application-local.yml` 以及 `.local/runtime.properties`，不用重复输入配置路径。若先前在终端导出过 `SPRING_CONFIG_ADDITIONAL_LOCATION`，可执行 `unset SPRING_CONFIG_ADDITIONAL_LOCATION`，避免旧设置干扰环境切换。

| 文件 | 用途 |
| --- | --- |
| `src/main/resources/application.yml` | 公共配置 |
| `src/main/resources/application-local.yml` | 本地配置，自动导入本机凭据文件 |
| `.local/runtime.properties` | 不入 Git、不打包的本机凭据；原测试工具继续使用 |
| `src/main/resources/application-prod.yml` | 生产配置，凭据由部署侧提供 |

需要通过 Maven 切换环境时使用 `mvn spring-boot:run -Dapp.profiles=prod`，这要求提前提供生产环境配置，不是本机验收命令。`prod` 同时激活既有 `production` 安全检查。不要同时启用 `local` 和 `prod`。

打包后的 JAR 不继承 Maven 的默认 local：本地运行可使用 `java -jar target/d2a/mujian-backend-0.1.0.jar --spring.profiles.active=local`；生产显式使用 `--spring.profiles.active=prod` 并由部署环境注入凭据。已有附加配置路径启动方式仍兼容。Profile 不会自动启动 MySQL/Redis，也不会自动迁移数据库。

Java Corretto 17.0.19、Maven 3.9.14、MySQL 8.4.10；Redis 使用本机现有 8.8.0 服务（本轮已核验），统一连接 127.0.0.1:6379。开发库为已存在的 darkmujian，不能清空。迁移与运行账号分离，授权范围见 [权限记录](deploy/LOCAL-ACCOUNT-PERMISSIONS.md)。

```sh
mvn -Dmj.build.directory=target/d2a -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/d2a/classpath.txt
# 现有开发库不重复迁移或初始化管理员。
# 先确认本机 Redis 6379 已运行；只有缺失时才使用 deploy/redis-development.conf 启动。
# 另开终端：
java -jar target/d2a/mujian-backend-0.1.0.jar --spring.config.additional-location=file:./.local/runtime.properties --spring.data.redis.port=6379
```

本批显式选择 target/d2a，避免 IDE 在默认 target/d1 输出目录写入的编译类互相覆盖；默认 Maven 构建目录仍为 target/d1。`.local/redis` 为此前从官方 7.2.16 源码构建的本机二进制，不入 Git。源码归档 SHA256：`65fa69e66187cb5a4cf625bd587c743507e518749c5a0eec29c4f086495c8a7e`。镜像 tag/digest 见 [container-lock.json](deploy/container-lock.json)，本轮没有启动 Docker 容器。

将 [runtime.example.properties](deploy/runtime.example.properties) 复制到 `.local` 后在本机填写秘密，目录权限 700、文件 600。迁移配置使用相同的 datasource 三个键及迁移账号；运行进程不执行 Flyway、不需要 DDL 权限。不要把 root 凭据交给应用。

管理员初始化：`python3 deploy/run-cli.py admin .local/runtime.properties`，通过终端隐藏输入密码；或追加本机 600 权限的初始化秘密文件路径（键 username/password）。重复执行保留原密码，事务末尾失败整体回滚。现有本机管理员为 d1.admin；随机初始密码仅在 `.local/admin-initialization.properties`，不放入文档。

## 配置与验证

配置键：`spring.datasource.url/username/password`、`spring.data.redis.host/port/password`、`mj.environment`（development/test/production）、`server.address/port`。生产默认使用 Secure 的 `__Host-mj_admin` Cookie，必须配置 HTTPS 入口；开发 Cookie 为 mj_admin_dev。不要将示例本机配置用于发布。

```sh
MJ_COMPAT_CONFIG="$PWD/.local/test/compatibility-20261027223122_de8afb.properties" \
MJ_TEST_CONFIG="$PWD/.local/test/integration-20261027223122_de8afb.properties" \
MJ_EVIDENCE_DIR=/path/to/sanitized-evidence \
python3 deploy/verify-local.py -Dmj.build.directory=target/d2a \
-Dtest=D2ApplicationTest,D2MigrationTest,MigrationIntegrationTest,ApiSessionIntegrationTest,OperationErrorMatrixTest,ServiceFailureIntegrationTest
```

两个测试配置分别只能指向 `darkmujian_d1_compat_*` 和 `darkmujian_d1_test_*` 的本机可销毁库；不能使用 darkmujian。未设置环境变量时测试会跳过，不能据此宣称验收通过。原D1为7项；C-06整改后11项在全新隔离库和重复运行中均无跳过，详见 [U-05](../../开发交接/D1-最小框架/U-05-D1整改验收摘要.md)。

代码入口：[迁移](src/main/resources/db/migration)、[会话](src/main/java/com/mujian/backend/common/security/SessionService.java)、[权限服务](src/main/java/com/mujian/backend/application/service/admin/AdminAccessService.java)、[集成测试](src/test/java/com/mujian/backend/integration/ApiSessionIntegrationTest.java)。会话与测试边界见 [工程说明](docs/D1-工程说明.md)。

### C-06 整改回归入口

使用 `MJ_TEST_CONFIG=/安全路径/隔离库.properties MJ_COMPAT_CONFIG=/安全路径/兼容库.properties python3 deploy/verify-local.py`。每个集成测试类自行 Flyway migrate/validate，专属随机管理员由测试创建清理，不依赖 d1.test.admin 或类顺序。未配置环境退出 2 并显示 NOT RUN；直接 Maven 的 skipped 不是验收通过。测试库须是本机 `darkmujian_d1_test_*`，不可指向 darkmujian。

新增菜单须同时注册后端 MenuRegistration bean 和 Web pageRegistry（静态 import），再交付增量菜单种子。数据库只校验安全路径形状；未注册标识不执行。禁止改 V1/V2 校验和。

## D2-A 本机验证与外部边界（2026-10-28）

复用受保护入口 `deploy/verify-local.py`，只认本次运行生成的报告，失败/跳过均拒绝。目前累计34个不同测试方法（本次D2ApplicationTest全23方法通过，另外11个未受影响方法沿用原证据），不代表AT/CL全通过。现有compat库已到V6，仅供D1→D2迁移重复验证；旧 FrameworkCompatibilityTest 需要自己的全新兼容库，不要在这里运行它。D1已过且未受影响的框架项不重复派发。

可复用测试配置保存在 `.local/test/`，目录700、文件600且Git忽略。首次准备另一套测试环境时执行 `python3 deploy/prepare-d2-tests.py --admin-user root`，终端隐藏输入管理密码；脚本拒绝覆盖同名数据库，账号只授权新建的两座测试库。应用绝不使用root。

设置同一 `MJ_TEST_CONFIG`、`MJ_EVIDENCE_DIR` 后运行 `python3 deploy/verify-d2-constraints.py`，全部反例事务回滚，仅测试库。`deploy/verify-d2-http-evidence.py` 需要 Python jsonschema，校验实际采样而非合成样例。`python3 deploy/sync-d2-codes.py --check` 核对101个Java显式稳定码与两端正式契约；无参数时从已批准契约重新生成 D2Codes.java。

V4为12表完整结构，V5为五规则正文，V6为账号资质菜单与六权限。正常启动不迁移；今后迁移必须先隔离验证，再显式 `python3 deploy/run-cli.py migrate .local/migration.properties --build-dir target/d2a`。本轮开发库已是V6，无需重复初始化。

微信与COS密钥仅填本机 `.local/runtime.properties` 或生产秘密管理。示例键见 deploy/runtime.example.properties；COS桶和地域由环境配置提供（见下方“COS 跨环境部署”）；既有对象前缀保持不变。清理开关保持false、cloud-policy-verified保持false。版本开启或策略未知时拒绝删除；逐版本物理回收尚未实现。实际云CL与匿名/签名检查通过前，不开启删除。AES密钥为独立32字节随机值，版本是AAD的一部分，不可丢弃仍被历史密文引用的旧版本。

本批可选隔离浏览器夹具位于 `src/test/java/com/mujian/backend/d2a/D2VisualFixturesCli.java`，只存在测试classpath，启动前必须设置MJ_TEST_CONFIG；使用真实测试库/Redis、虚构微信/COS与固定测试时钟。凭据写入700/600的.local/test目录，不进JAR。浏览器夹具的Vite代理需独立端口设置MJ_TEST_API_TARGET；原生可在停止正式18080后传入参数18080（其余固定端口拒绝），由受保护visual-runtime.json提供测试会话。完成后退出测试会话、关闭夹具并恢复正式JAR/5173→18080；不改小程序业务源码注入身份。

父删除外键专项：先运行D2ApplicationTest生成隔离夹具，再设置同一MJ_TEST_CONFIG与MJ_EVIDENCE_DIR，执行`python3 deploy/verify-d2-parent-fks.py`（需pymysql）。22条逐一使用独立父夹具验证RESTRICT，事务整体回滚；不用root、不关外键、不访问darkmujian。


### C-14整改续验（2026-10-28）

最新证据、失败复跑和未完成项见[开发交接C-15](../../开发交接/D2-准入与道具可租/C-15-D2-A整改续验记录.md)。39项D2定向通过，公共响应层影响的6项D1会话测试通过；HTTP组合303/311，静态预留7项待复核、真实微信上游异常1项待验。不代表整批签收。

本机MJ_TEST_CONFIG使用`.local/test/integration-20261028185351_4ebca4.properties`，MJ_COMPAT_CONFIG使用同后缀compatibility配置；两者700目录/600文件并Git忽略。prepare-d2-tests.py生成持久测试AES，禁止每次启动用同版本新key；旧测试库历史保留。受保护verify-local.py拒绝缺配置和跳过测试。验证与汇总命令见C-15，新的summarize-d2-remediation.py只分类实际样本/静态预留，不生成伪响应。

用户授权同网段手机联调：仅`.local/runtime.properties`调整server.address=0.0.0.0，正式JAR仍用local profile；公共application.yml默认127.0.0.1未变。手机地址目前192.168.1.6:18080，电脑loopback与5173继续可用；局域网测试结束可恢复本机监听后重启。未启动公网隧道，未改防火墙。手机号实际no permission须公众平台核对能力；密钥无需重填。

清理逐对象审计持久化意图再删除，结果不确定记unknown；两自动删除门禁保持false。真实COS样本最早2026-10-30 18:45:36北京时间满48h，安全定位信息在.local/test/real-channel-state.json，不得打印或归档其签名URL。测试夹具和故障代理已停止；正式18080/5173保留运行供用户联调。

本轮正式后端启动附加`-Djava.net.preferIPv4Stack=true`，已核实IPv4监听；当前LAN联调没有开启公网隧道或IPv6通配监听。


## D2-B 当前交付（2026-10-28）

B已在现有dev2工程实现，结果与限制以 [C-22](../../开发交接/D2-准入与道具可租/C-22-D2-B实施证据与剩余项.md) 和 [U-10](../../开发交接/D2-准入与道具可租/U-10-D2-B实施验收摘要.md) 为准，待总工复核。上文D1/A数量和过程性阻断为历史记录；通用说明已记录用户更正AppID后真实取号成功，不再追问旧no_permission。

工厂准入、完整资料变更、4个管理端页面、37操作均有实现；改号默认关闭且后端拒绝伪造phone/proof，普通资料可变更。B新迁移为V7/V8，已应用本地darkmujian；禁止修改已执行迁移或清库。正式后端使用`target/d2b/mujian-backend-0.1.0.jar --spring.profiles.active=local`，附加`-Djava.net.preferIPv4Stack=true`位于`-jar`前；读取既有`.local/runtime.properties`，当前18080已运行。

B定向前端命令：仓库根`node --test tests/factory-client.test.cjs tests/factory-admin.test.cjs`；实际响应schema测试另需MJ_EVIDENCE_DIR后执行`node --test tests/factory-contract.test.cjs`，没有采样会失败。管理端在admin-web运行`npm run build`。后端保护测试命令、持久测试配置和新库初始化方式见C-22第6节。不要用不带实采目录的旧全通配命令冒充本批验收。

本次隔离UI使用D2BVisualFixturesCli，仅测试classpath、真实测试MySQL/Redis及虚构微信/COS，已停止；5173→正式18080保留。原生IDE编译通过，原生工厂完整操作仍被窗口控制工具故障阻断；原型最终渲染对照未签收。不得把Node/浏览器证据代替真机通过。真实COS样本只存在私有B前缀，删除门禁保持关闭。


### C-22第8节增量修复（2026-10-29）

已知B-R01～04缺陷已修复，证据及原生/视觉余项见C-22第8.4～8.6节，待总工复核。当前正式本地后端使用`target/d2b-review/mujian-backend-0.1.0.jar`（local profile、既有LAN配置，`-Djava.net.preferIPv4Stack=true`位于`-jar`前），18080/5173保留，隔离5175已停止。此处覆盖上文历史构建路径；未执行新迁移，禁止修改已应用V7/V8。

前端根`node scripts/sync-response-contracts.cjs --check`核对正式D1/A/B派生响应注册表及B三份运行副本；无--check时重新生成。共享请求按具体操作完整schema分类可信业务拒绝、会话失效及未知结果。仅增量实采契约验证时同时指定`MJ_INCREMENTAL=1`和新`MJ_EVIDENCE_DIR`，使用既有factory-contract.test.cjs，缺实际批准错误样本会失败，不会跳过。无关已过项继续复用，不重复全量或迁移验证。

## 本人开发身份维护（C-27 §11.6，仅人工调用）

`deploy/identity_maintenance.py` 是独立维护命令，不是 Spring 参数、HTTP API 或产品功能。生产 JAR 不包含 deploy 脚本，生产启动入口不调用它。依赖现有 Python3/PyMySQL；本机使用 `/Users/mbro/.local/bin/python3`。默认配置模板 `deploy/identity-maintenance.example.json` 的 enabled=false。实际配置只在 `.local/identity-maintenance/config.json`，精确单账号 allowedUserId 与独立 enrollment.json（本人真实登录上下文、trace、暂停确认、MySQL server_uuid）必须相符。不得把示例占位值当实际登记。

每次同时核验 enabled=true、`--profile local`、工具 environment=local、本机统一 runtime 配置、127.0.0.1:3306/darkmujian、MySQL服务UUID和本机hostname、原唯一索引及完整结构指纹。无任意用户/数据库/SQL参数；D3等新增结构会拒绝，先复核新依赖。固定系统账号、管理员、禁用账号、在审经营操作、真实交易/资金/占用及未知关联拒绝。关联检查覆盖外键及逻辑ID/JSON引用，行内容仅在服务端生成哈希，证件/照片/手机号不写报告。

操作前：所有使用此账号的设备暂停写入，清除该账号旧页面、登录态及待恢复命令（只处理目标账号，不能清全Redis或其他账号缓存）；使用调试控制台时先核对当前 context.userId，再仅删除 `mj.pending.<目标userId>.`、`mj.prop.command.<目标userId>.` 对应键以及该设备当前目标账号的 `mj.session`，重新启动页面。检查工厂服务使用的其他待恢复键同属 `mj.pending.<userId>.`。不能确认设备清理时，不传 `--device-reset-confirmed`，工具拒绝写入。用户暂停不是设备已清理的证明。

写维护必须先正常停止本机后端并等待JVM退出；工具同时检查18080和其他后端JVM，不支持带着在途业务切换。其他用户/管理员会话保留，但本机维护期间HTTP会短暂停服。MySQL/Redis不停止。恢复时使用原JAR及local profile。读检查不必停后端。

```sh
# 工作目录为后端；reason记录真实用途，工具记录OS操作者，不冒充产品管理员。
python3 deploy/identity_maintenance.py inspect --profile local --account-paused --reason '本人开发身份只读核对'
# 首次工厂准备：须完成备份和设备清理，不是直接生成工厂。
python3 deploy/identity_maintenance.py bootstrap --profile local --account-paused --device-reset-confirmed --reason 'C-27 11.6首次正常工厂入驻准备'
# 正常邀请/提交/审批完成，确有两种身份后才可执行以下命令。
python3 deploy/identity_maintenance.py switch --to demander --profile local --account-paused --device-reset-confirmed --reason '本人个人端开发验收'
python3 deploy/identity_maintenance.py switch --to supplier --profile local --account-paused --device-reset-confirmed --reason '本人工厂端开发验收'
```

开启仅编辑本机config.json的enabled为true；关闭改为false，即禁止后续维护，不影响正常登录和当前身份，不删除任何历史，不自动回个人。关闭前先选定最终身份。切换必须先禁用旧行再启用现有目标行，并在同一事务更新stage；保留唯一守卫。同身份重复命令为no_change，不造数据，不注销会话。首次准备只把现有demander行停用并置new；其后邀请锁定/pending/rejected阶段不允许往返。必须正常审批形成supplier/factory再使用switch。

每次写操作在 `.local/identity-maintenance/<操作号>-prepared.json` 先持久化目标sys_user/角色原行、关联统计/哈希、真实OS操作者、原因和计划；提交结果另存 `-result.json`，失败存 `-failure.json`，目录700/文件600/Git忽略。原行备份含必要个人信息，不复制到交接文档。SQL失败回滚，已撤销会话不恢复。COMMIT通信异常或提交后审计落盘异常标记commit_outcome_requires_inspection，禁止自动重试；核对DB与prepared后人工处置。

仅首次bootstrap后仍为new、原demander行仍停用、无任何工厂/邀请/申请等依赖时，支持 `rollback-bootstrap --operation <原已提交bootstrap操作号>`（其余安全参数同上）。只恢复角色启用和stage，不整行覆盖备份、不回写电话/认证/首次事实。正常工厂形成后只能在已形成两身份之间switch；不以恢复整库或删除工厂回退。新增在途业务/不明关联须停住，不能自动清理。

最小验证入口沿 `deploy/verify-local.py` 指定 `LocalIdentityMaintenanceTest`；需现有MJ_TEST_CONFIG/MJ_COMPAT_CONFIG隔离配置。Python适配器仅在 `deploy/tests/`，强制darkmujian_d1_test_*，无生产测试绕过参数。实际本人手机往返仍须等正常工厂入驻完成；隔离库测试不能代替真机。


## D3-A 运行与依赖（2026-11-06）

新增方案/分享/PDF、租期清单和地址能力；执行结果以[C-02](../../开发交接/D3-最小首次交易/C-02-D3-A整批实施任务单.md)为准。显式迁移V12已在本机开发库执行，启动仍不自动迁移。独立产物为`target/d3a/mujian-backend-0.1.0.jar`；用户已指定D3执行者兼任本机运行负责人，2026-11-06按授权启用该版本（18080/local/darkmujian）。此前各D2段落运行路径为历史。当前PID/制品哈希及可恢复启动参数见本机600权限的`.local/d3-runtime-switch.json`，脱敏结果见C-02原证据索引。配置未改、启动未迁移，禁止重复初始化。

PDF使用`org.apache.pdfbox:pdfbox:3.0.8`（Apache-2.0），Java版本与现有Spring/MySQL版本保持不变。字体`src/main/resources/fonts/NotoSansSC-Regular.ttf`从[Noto CJK官方SC可变字体](https://github.com/notofonts/noto-cjk/tree/main/Sans/Variable/TTF/Subset)实例化为Regular 400；随包保留[OFL.txt](src/main/resources/fonts/OFL.txt)（SIL OFL 1.1）。固定模板版本`scheme_v1`；源字体、许可证与固定快照不含用户凭据。PDF不上传COS，服务器文件仅经鉴权Controller下载。

|配置|默认|用途|
|---|---|---|
|`mj.planning.full-save-max-bytes`|1048576|只用于方案全量保存/重排和清单全量保存|
|`mj.planning.pdf.directory`|`${java.io.tmpdir}/mujian-scheme-pdf`|启动检查可写，专用私有目录权限700；不位于.local或静态Web目录|
|`mj.planning.pdf.concurrency`|2|单实例非阻塞生成容量|
|`mj.planning.pdf.max-file-bytes`|20971520|单次实际输出上限20MiB|
|`mj.planning.pdf.total-bytes`|1073741824|临时目录容量预算1GiB|

2026-11-18候选规则：每用户上海自然日5次（已提交任务失败也计）、同方案从任务创建起3分钟冷却、完成后5分钟私有文件、3分钟Redis定位、120秒生成截止、7天终态记录。统一POST无前端版本/请求号，授权后按当前已保存内容查专属`mj:{env}:scheme-pdf-export:`键；命中不续期不计次，未命中不从数据库或磁盘查旧成品。V22仅隔离验证，共享迁移与切包另行安排，上文运行记录不表示本候选已启用。每分钟扫描处理超时和文件清理；文件读租约与清理互斥。当前方案仅承接单后端实例，多实例部署须先满足C-04的受控共享临时存储或拥有节点路由条件。

```sh
mvn -Dmj.build.directory=target/d3a -DskipTests package dependency:build-classpath -Dmdep.outputFile=target/d3a/classpath.txt
# 先核对端口/进程及使用者；当前已运行，不要重复启动。
java -Djava.net.preferIPv4Stack=true \
  '-Dhttp.nonProxyHosts=localhost|127.*|cct1-1429809468.cos.ap-shanghai.myqcloud.com' \
  -jar target/d3a/mujian-backend-0.1.0.jar --spring.profiles.active=local \
  --spring.config.additional-location=file:.local/runtime.properties
```


D3独立HTML只读分享源文件位于兄弟前端仓库`../mujian-frontend/share/`，Maven构建只复制`scheme.html/js/css`到JAR的`static/share/`，不可另维护一套副本。入口`/share/scheme.html?token=<当前分享token>`与原匿名API同源；静态权限精确GET三路径，POST或其他路径不开放。页面禁缓存/referrer，隐藏价格完全去字段/DOM，重新进入与图片续取仍查同一分享；前端复制链接使用现有environment.baseUrl。LAN链接只对可达该主机的设备有效，未配置公网域名时不声称公网可用。

本机JVM已有显式HTTPS代理；真实COS测试证明该已知COS主机经代理失败、直连成功，故当前启动参数仅为localhost/127.*及固定COS主机设置nonProxyHosts。未改密钥、系统代理或TLS验证。PDF仍留服务器私有临时目录，不上传COS。真实COS测试只读取既有虚构测试图，证据见C-02。

`D3NativeFixturesCli`仅存在测试classpath，复用保护测试库和虚构渠道，以随机API端口及loopback bootstrap提供短期原生联调夹具；不打入生产JAR。不得将其token写入业务源码或共享配置。2026-11-06一轮使用完毕已停止、移除临时凭据文件并恢复开发工具原guest会话及正式请求。开发工具结果、仍存基础库诊断与手机待验边界均以C-02为准。


D3-A R1—R4当前运行补充（2026-11-06）：新产物在`target/d3a-r1-r4/mujian-backend-0.1.0.jar`，PID98872/18080，沿既有local配置与darkmujian；上文target/d3a为前轮已保留产物。为排除移动record后的旧class残留，本轮使用全新构建目录；后续构建/启动命令须使用该当前目录。原启动信息在`.local/d3-runtime-switch-before-r1-r4.json`，当前信息在`.local/d3-runtime-switch.json`，均600权限。源码中方案/地址专属DTO/VO已归所属模块；PDF外部读取由`infrastructure/storage/BoundedMaterialReader`承接，仍复用PrivateMaterialStore签名，限时限量/HTTPS/禁重定向保持。R1前端公共流程和受影响验证结果见原C-02/U-01，不重跑无关验收。


## COS 跨环境部署（2026-11-24）

统一使用腾讯云 Java SDK 与默认 HTTPS 桶域名，当前为 `https://cct1-1429809468.cos.ap-shanghai.myqcloud.com`。不固定 IP、不关闭 TLS 校验，上传继续指定私有 ACL，桶保持私有。对象路径、接口和签名期限不变；换服务器或桶只需改外部配置并重启同一 JAR，不重新打包。

| 配置键 | 环境变量 | 默认／说明 |
| --- | --- | --- |
| `mj.cos.bucket` | `MJ_COS_BUCKET` | `cct1-1429809468`，包含 APPID 后缀 |
| `mj.cos.region` | `MJ_COS_REGION` | `ap-shanghai` |
| `mj.cos.secret-id` / `mj.cos.secret-key` | `MJ_COS_SECRET_ID` / `MJ_COS_SECRET_KEY` | 无默认凭据，由各环境秘密管理注入 |
| `mj.cos.proxy.host` / `mj.cos.proxy.port` | `MJ_COS_PROXY_HOST` / `MJ_COS_PROXY_PORT` | 空／0，不主动配置 SDK 代理；host 为出口代理域名或地址，不带协议 |
| `mj.cos.proxy.username` / `mj.cos.proxy.password` | `MJ_COS_PROXY_USERNAME` / `MJ_COS_PROXY_PASSWORD` | 可选代理认证，同样安全注入 |

**Mac 本地：** 保持 Clash 开启，使用 `local`。桶、地域及凭据放 `.local/runtime.properties`（权限 600，忽略且不打包），示例见 [runtime.example.properties](deploy/runtime.example.properties)。`LocalCosNetworkConfiguration` 仅在 Mac、local 且未显式配置 SDK 代理时，在客户端创建前向 JVM 的 `http.nonProxyHosts`、`socksNonProxyHosts` 追加当前桶域名，保留并去重原有例外，不改变系统代理或 JVM 代理地址。域名随 bucket/region 配置计算。无需把本机 Clash 地址写入业务代码或云端配置。

**管理端道具图片：** 审核资料、资产详情及二维码异常证据使用同源 `GET /api/admin/prop-materials/content`。浏览器携带管理端会话，后端按材料所属业务校验读取及敏感权限，记录查看审计，再通过同一 COS SDK 返回私有 PNG／JPEG，响应禁止缓存。管理端部署须将站点的 `/api` 转发到后端。只要浏览器能访问管理端站点，图片读取便使用同一入口，COS 网络配置由后端所在环境管理。

**云端直连：** 使用 `prod`，不携带 Mac 的 JVM 代理参数、`JAVA_TOOL_OPTIONS`／`JDK_JAVA_OPTIONS` 中的代理设置或本机 local 配置。通过秘密管理挂载只读配置文件，例如 `/etc/mujian/runtime.properties`，保留服务器本身所需的数据库等配置，并加入：

```properties
mj.cos.bucket=cct1-1429809468
mj.cos.region=ap-shanghai
mj.cos.proxy.host=
mj.cos.proxy.port=0
# secret-id、secret-key 由秘密管理写入，禁止提交真实值。
```

```sh
java -jar mujian-backend-0.1.0.jar --spring.profiles.active=prod \
  --spring.config.additional-location=file:/etc/mujian/runtime.properties
```

也可由部署环境安全注入上表环境变量；不要把密钥放启动命令或公开日志。`prod` 的数据库、安全等既有启动要求仍须满足。

**云端需出口代理时：** 只在该环境外部配置中设置，例如 `mj.cos.proxy.host=egress.internal.example`、`mj.cos.proxy.port=3128`，可选填写代理认证。SDK 使用该 HTTP 出口代理访问 HTTPS COS；默认桶域名和手机拿到的 HTTPS 签名 URL 不变。本项不是 SOCKS 代理配置。不要复制 Mac 的 `127.0.0.1:7897`。

**验收边界：** 必须经过原上传接口实际写入，再验证签名图片可读取、手机可重新打开；匿名 403 仅作私有访问限制的辅助检查。当前云端未部署，云端直连／出口代理实网结果均待验；本地结果见 [03 执行记录末节](../../开发交接/03-Agent执行规范与验收清单.md)。
