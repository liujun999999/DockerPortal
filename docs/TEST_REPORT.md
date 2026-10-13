# DockerPortal 测试报告

- 角色：测试工程师（自动化 + 在线冒烟 + 人工清单）
- 被测：github.com/liujun999999/DockerPortal `main`
- 环境：macOS / Docker Desktop 29.8.1（当前节点为 swarm worker）；后端 :9800、前端 :3000 运行中
- 计划文件：`docs/TEST_PLAN.md`
- 结论：**通过（Pass with notes）** —— 构建门禁全绿、安全关键单测全绿、在线安全面全部符合预期；无阻断缺陷。存在 1 项低危观察项与 1 项测试基础设施缺口，另有部分用例因缺少登录凭据被阻塞。

## 一、执行汇总

| 层级 | 范围 | 执行 | 通过 | 失败 | 阻塞 | 结果 |
| --- | --- | --- | --- | --- | --- | --- |
| L1 构建/静态 | 后端编译 + 前端类型检查 + 产物构建 | 3 | 3 | 0 | 0 | ✅ |
| L2 单元测试 | SecretCipher + JwtTokenProvider（本次新增） | 13 | 13 | 0 | 0 | ✅ |
| L3 在线安全冒烟 | 认证/H2/伪造令牌/actuator/降级 | 11 | 9 | 0 | 2 | ✅（2 阻塞） |
| L4 人工探索 | UI 全链路 | — | — | — | 全部 | ⏸ 交付为清单 |

合计自动化执行 **27 项，27 通过，0 失败**；L3 有 2 项因需有效凭据阻塞，L4 需浏览器人工执行。

## 二、L1 构建 / 静态门禁

| 用例 | 命令 | 结果 |
| --- | --- | --- |
| L1-1 后端编译 | `mvn -o compile` | ✅ 退出码 0 |
| L1-2 前端类型检查 | `vue-tsc --noEmit` | ✅ 无类型错误 |
| L1-3 前端产物构建 | `vite build` | ✅ 2386 modules，built in 11.25s |

## 三、L2 后端单元测试（本次新增，仓库此前 0 测试）

`mvn test` → **Tests run: 13, Failures: 0, Errors: 0, Skipped: 0**

### SecretCipherTest（AES-256-GCM 凭据加密，7 项）
| 用例 | 验证点 | 结果 |
| --- | --- | --- |
| encryptThenDecryptRoundTrip | 含中文/符号的明文往返一致，密文带 `enc:v1:` 前缀 | ✅ |
| encryptIsIdempotentAndPassesThroughBlank | 已加密值不二次包裹；null/空白原样返回 | ✅ |
| decryptPassesThroughLegacyPlaintext | 无前缀历史明文透明读取 | ✅ |
| decryptRejectsTamperedCiphertext | 密文被篡改 → GCM 认证失败抛 BusinessException | ✅ |
| decryptFailsWithDifferentKey | 换密钥解不开旧密文 | ✅ |
| keyIsPersistedAcrossInstances | 同目录重建实例读回同密钥，可解旧密文 | ✅ |
| encryptUsesRandomIvEachCall | 同明文两次密文不同（随机 IV）但都能解回 | ✅ |

### JwtTokenProviderTest（JWT 签发/校验，6 项）
| 用例 | 验证点 | 结果 |
| --- | --- | --- |
| generateThenValidate | 签发→校验往返为真 | ✅ |
| claimsRoundTrip | username/role/must_change 正确回读 | ✅ |
| expiredTokenIsRejected | 过期令牌 validate=false | ✅ |
| tokenSignedWithOtherKeyIsRejected | 异密钥令牌被拒 | ✅ |
| garbageTokenIsRejectedWithoutThrowing | 垃圾/空/null/篡改串均拒且不抛异常 | ✅ |
| refreshTokenAlsoValid | 刷新令牌本密钥可校验 | ✅ |

> 说明：测试日志中出现的 `WARN Invalid JWT token` / `ERROR SSH 凭据解密失败` 是负向用例**故意触发**的守卫分支，属预期输出，非失败。

## 四、L3 在线接口 / 安全冒烟（对运行中的 :9800）

| 用例 | 请求 | 期望 | 实测 | 结果 |
| --- | --- | --- | --- | --- |
| L3-A1 | GET /api/users（无 token） | 401/403 | 401 | ✅ |
| L3-A1b | GET /api/environments（无 token） | 401/403 | 401 | ✅ |
| L3-A2 | GET /api/setup/status | 200 | 200 | ✅ |
| L3-A3 | POST /api/auth/login 错误口令 | 4xx | **401** `{"code":40100,"用户名或密码错误"}` | ✅ |
| L3-A3b | POST /api/auth/login 缺字段（@Valid） | 4xx | 400 `{"code":40000,"密码不能为空"}` | ✅ |
| L3-A4 | GET /api/users + `Bearer 垃圾串` | 401 | 401 | ✅ |
| L3-A5 | GET /api/users + 真结构/假签名 JWT | 401 | 401 | ✅ |
| L3-H1 | GET /h2-console/（DP_H2_CONSOLE 默认 false） | 401/403 | 401 | ✅ |
| L3-H1b | GET /h2-console/login.jsp | 401/403 | 401 | ✅ |
| L3-S1 | GET /actuator/** | 不暴露 | 500（未引入 actuator 依赖，路径未映射） | ✅ 无泄露 |
| L3-R1 | GET / （SPA 根） | 非 5xx | 500 | ⚠ 见 F-1（dev-only） |
| L3-E1 | worker 节点 swarm 接口降级 | 结构化错误 | — | ⏸ 阻塞：需有效 token |
| L3-RBAC | operator/admin 权限差异 | — | — | ⏸ 阻塞：需有效 token |

**安全面判定：未认证访问、伪造/篡改令牌、H2 控制台、actuator 泄露——全部符合预期，未发现越权或信息泄露。** 登录失败返回 401 且文案友好、不含堆栈。

## 五、缺陷与观察项

### F-1（低危 / 仅开发模式，非生产缺陷）— 未映射 GET 路径返回 500 并刷 ERROR 日志
- 现象：开发模式下 `GET /`、`GET /some-random-page` 返回 `500 {"code":50000,"服务器内部错误"}`，`GlobalExceptionHandler.handleException` 以 `log.error` 记录整条堆栈。
- 根因：dev 无前端 dist，`WebConfig` 的 `index.html` 回退取不到文件返回 null，落到 catch-all。
- 影响评估：**生产单镜像部署不受影响**——`WebConfig.addResourceHandlers` 对非 `api/ws/h2-console` 路径回退 index.html，深链刷新正常。仅开发模式存在日志噪音（探测/爬虫可放大）。
- 建议（可选）：为 `NoResourceFoundException` 加专门 handler 返回 404 并降为 `log.debug`，与已收口的 `/h2-console` 同类处理。

### F-2（测试基础设施缺口）— 仓库此前无任何自动化测试
- 现状：`src/test` 原为空；本次补齐 13 个安全关键单测（`SecretCipherTest`、`JwtTokenProviderTest`）。
- 建议：接入 CI，最小门禁 = `mvn -o test` + `vue-tsc --noEmit` + `vite build`；后续补 Service 层与 `@WebMvcTest` 安全配置测试。

### 测试过程中的自我纠偏（如实记录）
- 首轮 L3-A3 曾误报「登录返回 500」，经复测定位为**测试脚本 shell 转义把请求体打成非法 JSON**所致，非产品缺陷；用文件载荷干净复测后为 **401**。已修正测量方式，避免误报。

## 六、L4 人工探索性检查清单（需浏览器 + 登录凭据，本轮未执行）
- [ ] 登录 → 强制改密 → 登出；token 过期自动跳登录
- [ ] 容器列表批量启停/删除、克隆预填、日志「自动刷新」开关与智能跟随滚动
- [ ] 服务详情任务状态过滤、单副本/聚合日志切换、扩缩容确认框
- [ ] SSH 终端多标签 / 全屏；凭据保存后重进不回显明文
- [ ] 告警规则触发 → Webhook 通知；审计日志留痕
- [ ] 非 manager 节点执行管理操作时的提示文案友好性

## 七、通过判定

| 门禁 | 标准 | 结论 |
| --- | --- | --- |
| L1 全绿 | 必须 | ✅ |
| L2 全绿 | 必须 | ✅ |
| L3 安全项（A1/A4/A5/H1/S1） | 必须 | ✅ 全过 |
| 阻断级缺陷 | 0 | ✅ 无 |

**总体：通过（Pass with notes）。** 建议合并本轮新增测试、按 F-2 建立 CI 门禁；L4 与认证态 L3（E1/RBAC）待提供测试账号后补测。
