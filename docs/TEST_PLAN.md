# DockerPortal 测试计划

> 角色：测试工程师。目标：在缺乏既有自动化测试的现状下，建立可重复执行的质量门禁，
> 覆盖构建、安全关键逻辑单测、在线接口/安全冒烟三层，并给出人工探索性检查清单。
> 所有结论以真实执行结果为准，不臆测。

## 被测版本 / 环境

- 仓库：github.com/liujun999999/DockerPortal，分支 main
- 后端：Spring Boot 3.2.5 / Java 21，运行于 :9800
- 前端：Vue3 + Vite，dev 运行于 :3000
- 运行时 Docker：Docker Desktop 29.8.1（当前节点为 swarm **worker**，非 manager）

## 测试分层与用例

### L1 构建 / 静态门禁（必须全绿，阻断式）
| 编号 | 用例 | 命令 | 判据 |
| --- | --- | --- | --- |
| L1-1 | 后端编译 | `mvn -q -o compile` | 退出码 0 |
| L1-2 | 前端类型检查 | `vue-tsc --noEmit`（含在 build 内） | 无类型错误 |
| L1-3 | 前端产物构建 | `vite build` | 退出码 0，产物生成 |

### L2 后端单元测试（安全关键纯逻辑，本次新增）
| 编号 | 用例 | 关注点 |
| --- | --- | --- |
| L2-S1 | 加密解密往返 | encrypt→decrypt 还原明文；密文带 enc:v1: 前缀 |
| L2-S2 | 加密幂等 | 对已加密值再 encrypt 不二次包裹；空/空白原样返回 |
| L2-S3 | 历史明文透明读 | 无 enc: 前缀的 stored 直接原样 decrypt |
| L2-S4 | 篡改检测 | 密文被改动 / 换密钥后 decrypt 抛错（GCM 认证标签） |
| L2-S5 | 密钥持久化 | init 生成 .secret.key；重建实例读回同密钥可解旧密文 |
| L2-J1 | 令牌签发/校验往返 | generateAccessToken→validateToken=true |
| L2-J2 | 声明还原 | username / role / must_change 正确回读 |
| L2-J3 | 过期令牌失效 | 过期后 validateToken=false |
| L2-J4 | 异密钥令牌拒绝 | 换 secret 签发的令牌 validateToken=false |
| L2-J5 | 垃圾令牌拒绝 | 非 JWT 串 validateToken=false |

### L3 在线接口 / 安全冒烟（对运行中的 :9800 执行）
| 编号 | 用例 | 判据 |
| --- | --- | --- |
| L3-A1 | 未认证访问受保护接口 | `GET /api/users` 无 token → 401/403 |
| L3-A2 | 匿名白名单 | `GET /api/setup/status` → 200 |
| L3-A3 | 登录接口存在且校验 | 错误口令登录 → 非 2xx，不泄露内部信息 |
| L3-H1 | H2 控制台默认关闭 | `GET /h2-console/` → 401/403（DP_H2_CONSOLE 默认 false） |
| L3-E1 | worker 节点降级 | swarm 管理接口在 worker 上返回结构化错误而非 500 堆栈 |
| L3-R1 | SPA 根路径 | `GET /` 有转发处理（非 500） |

### L4 人工探索性检查清单（需浏览器，本轮以清单交付 + 尽量 curl 佐证）
- 登录 → 改密 → 登出全链路；token 过期后跳登录
- 容器列表批量启停/删除、克隆预填、日志自动刷新开关与跟随滚动
- 服务详情任务状态过滤、副本日志、扩缩容确认框
- SSH 终端多标签 / 全屏；凭据保存后重进不回显明文
- 告警规则触发 → Webhook；审计日志记录
- 非 manager 节点操作提示文案是否友好

## 执行方式

- L1、L2：命令行自动执行，纳入可重复门禁（`mvn test`）
- L3：curl 脚本对运行实例执行
- L4：交付为清单，抽样用 curl / 构建佐证

## 通过标准

L1 全绿；L2 全绿；L3 安全项（A1/H1）必须通过，其余按实际环境判定并如实记录。
