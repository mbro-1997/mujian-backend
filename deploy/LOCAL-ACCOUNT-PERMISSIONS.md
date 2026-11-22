# 待用户授权的本机账号权限变更

目标仅为本机 MySQL `darkmujian`；禁止全局权限、远程账号或修改 root。

- `mj_d1_migrate`@`127.0.0.1`：SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON darkmujian.*。用于显式 Flyway 迁移；不授予 DROP、GRANT OPTION 或用户管理。
- `mj_d1_runtime`@`127.0.0.1`：SELECT, INSERT, UPDATE, DELETE ON darkmujian.*；没有 DDL。
- 两个密码独立随机生成，只保存到本机被 Git 忽略且 0600 的配置；不打印，不写日志或报告。
- 若账号已存在，停止并核查，不重置或接管现有账号。
- 不创建其他应用开发库、不清空重建 darkmujian。

此前自动审批拒绝了包含开发库持久授权的请求。本方案收窄权限并列出全部范围，等待用户明确授权；隔离可销毁测试环境不依赖此授权。
