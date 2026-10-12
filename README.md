# DockerPortal

> 一个开箱即用的 Docker / Swarm 集群管理面板：单容器部署、多环境接入、内置 SSH 终端与文件管理，中文原生。

<!-- 仓库地址：<https://github.com/liujun999999/DockerPortal> -->
![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green)
![Vue](https://img.shields.io/badge/Vue-3.4-brightgreen)
![Docker](https://img.shields.io/badge/deploy-docker--compose-2496ED)
![Status](https://img.shields.io/badge/status-actively%20developed-yellowgreen)
![QQ群](https://img.shields.io/badge/QQ%E7%BE%A4-1064928035-12B7F5)

## 为什么做这个

管理 Docker 的工具不缺：Portainer 功能全面但配置项繁多、社区版对多环境做了限制；docker cli 强大但没有团队入口；各大云厂商的控制台又和自家绑定。

我想要的其实很具体：**一个 jar 跑起来，能同时管本机、远程 TCP 和跳板机后面的 Swarm 集群；运维能自己动手，不用什么都找管理员；出事之后能查是谁干的。**

所以 DockerPortal 是这么做的：

- **单容器交付**：一个镜像内含前后端，`docker compose up -d` 即可，不需要数据库、不需要反向代理就能跑。
- **多环境统一入口**：本机 socket / 远程 TCP / SSH 三种连接方式并存，随时切换，凭据加密落库。
- **权限与审计是地基不是插件**：三档角色 + 破坏性操作分级确认 + 全量审计日志。
- **表单对齐 docker cli**：能用命令行做的事，界面上基本都有对应字段，不做"只能看不能改"的玩具面板。

## 特性一览

**容器与镜像**

- 创建容器：端口 / 环境变量 / 卷挂载 / 网络 / 命令 / 重启策略 / 资源限制，全表单化
- 生命周期操作、重命名、**运行中热更新 CPU 与内存限制**
- 实时日志（WebSocket 流式，支持全屏）、交互式 `bash` 终端（xterm.js）
- 容器内文件浏览与编辑（走 Docker archive 接口，文本编辑器带行号与语法高亮）
- 镜像拉取 / 推送 / 打标签 / 分层历史 / Dockerfile 构建
- **使用中的镜像不提供强制删除**：被容器（含已停止）或 Swarm 服务引用时，前后端双重拦截并在提示里列出引用方

**Swarm 集群**

- 集群初始化 / 加入令牌 / 离开、节点列表与**节点详情页**（概览 / 标签 / 本节点任务）
- 节点标签编辑与排空（drain）/ 激活
- 服务创建与更新（副本、调度约束、更新策略、健康检查、环境变量、卷与 Secret/Config 挂载）、扩缩容、任务列表与按状态过滤
- Stack 部署：粘贴 compose 或**上传 YAML 文件**，compose 原文由平台落库可再次编辑
- Secrets 与 Configs 管理，服务日志走 `/tasks/{id}/logs` 端点

**多环境与远程主机**

- 环境管理：`本机 socket` / `TCP` / `SSH`，保存前可先测试连接
- 内置 SSH 终端，多标签页，可直接开在 Swarm 宿主机上排查问题
- 私有镜像仓库凭据统一管理，拉取时自动注入

**运维与治理**

- 磁盘占用视图：逐列对齐 `docker system df`（含 Docker 29 的 daemon 侧汇总），并按资源类型提供 prune
- 监控仪表盘：容器 CPU / 内存 / 网络 / 磁盘 IO 实时曲线（ECharts + WebSocket）
- 告警规则与 Webhook 通知、告警事件历史（指标自动评估排在下一阶段，见「路线图」）
- 审计日志：谁在什么环境对哪个资源做了什么，含来源 IP
- RBAC 三档权限 + 首次登录强制改密 + 破坏性操作打字确认

## 界面预览

<!--
  截图放 docs/screenshots/ 后取消本段注释，四张图保持同一窗口尺寸（建议 1440x900）：
  | 总览 | 容器列表 | 服务详情 | SSH 终端 |
  | --- | --- | --- | --- |
  | ![dashboard](docs/screenshots/dashboard.png) | ![containers](docs/screenshots/containers.png) | ![service](docs/screenshots/service.png) | ![ssh](docs/screenshots/ssh.png) |
  快速出图：面板跑起来后 `docker run --rm -v "$PWD:/w" playwright image <URL> /w/shot.png --viewport-size=1440,900 --wait-for-timeout=6000`
  （登录态页面需要用 Playwright codegen 录一段脚本再截。）
-->

这里放四张截图：总览页（磁盘占用 + 服务概览）、容器列表、服务详情、SSH 终端。想帮忙补图的贡献者欢迎提 PR。

## 快速开始

```bash
git clone https://github.com/liujun999999/DockerPortal.git
cd DockerPortal
cp .env.example .env          # 全部留默认即可，密钥会在首次启动时自动生成
docker compose up -d --build  # 首次构建约 3~5 分钟
```

打开 <http://localhost:9800>，全新实例会自动进入**初始化向导**：

1. **创建管理员账号** —— 自己填用户名与密码（至少 8 位、含字母与数字）
2. **连接第一个 Docker 环境** —— 本机 socket / 远程 TCP / SSH，可先测连接再保存
3. **初始化 Swarm（可选）** —— 单节点也有价值，也可以稍后再做

向导同一次启动里会在数据目录生成随机 JWT 签名密钥 `.jwt.key`（权限 600）。装完建议把 `DP_SETUP_ENABLED=false`，关掉这个匿名的初始化入口。

**无人值守（IaC）部署**：设置 `DP_ADMIN_PASSWORD` 后启动，会按该密码创建 `admin` 并保留首次登录强制改密，不再弹建号步骤。

### 配置项

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DP_SERVER_PORT` | `9800` | Web/API 监听端口（容器内外一致） |
| `DP_JWT_SECRET` | 空 | 留空则自动生成 `.jwt.key`；自行托管时可用 `openssl rand -base64 48` 生成 |
| `DP_SETUP_ENABLED` | `true` | 匿名初始化入口开关，装完建议置 `false` |
| `DP_ADMIN_PASSWORD` | 空 | 设置后走无人值守建号 |
| `DP_DATA_PATH` | `./data` | 数据目录：H2 库 + `.secret.key` + `.jwt.key` |
| `TZ` | `Asia/Shanghai` | 时区，影响日志与审计时间 |

卡在哪一步（连不上远程环境、权限不够、镜像拉不下来）都可以直接去 [交流与反馈](#交流与反馈)，QQ 群 **1064928035**。

## 安全须知

- 管理本机 Docker 时容器需要挂载 `/var/run/docker.sock`。**能访问 docker.sock 等价于宿主机 root**，请仅在可信主机启用；只管理远程环境时可以移除该挂载。
- SSH 与镜像仓库凭据使用 **AES-256-GCM** 加密落库，密钥是数据目录下的 `.secret.key`。
- 备份必须包含整个数据目录：只拿库文件而丢了 `.jwt.key`，重启后所有已登录令牌失效；丢了 `.secret.key`，已存的凭据无法再解密（需重填）。
- 项目本身不做 TLS 终止，对外暴露请置于反向代理（Nginx / Caddy）之后并启用 HTTPS。

## 权限模型

| 角色 | 说明 | 典型能力 |
| --- | --- | --- |
| **VIEWER** | 只读 | 浏览容器/镜像/网络/卷、看日志与监控 |
| **OPERATOR** | 日常运维 | 创建/启停/删除容器、拉取/打标签镜像、容器终端与文件写、部署/扩缩容/删除服务与 Stack、单类型 prune |
| **ADMIN** | 基础设施 | 以上全部 + 环境/用户/仓库/告警规则配置、卷删除、`volume`/`system prune`、Swarm 变更、SSH 终端 |

破坏性操作分级确认：常规删除走二次确认；**卷删除、Stack/服务删除、`volume prune`、`system prune`** 需要手动输入指定文本才能执行。

## 架构与技术栈

```
DockerPortal/
├── dockerportal-server/       # Spring Boot 后端：21 个 Controller、WebSocket、SSH、安全与审计
│   └── src/main/java/com/dockerportal/
│       ├── controller/        # HTTP API
│       ├── service/           # 业务编排（Docker / Swarm / Stack / prune / 告警 / 审计）
│       ├── docker/            # docker-java 客户端工厂与连接池管理
│       ├── ssh/               # MINA SSHD 客户端
│       ├── websocket/         # 日志流、容器终端、监控、SSH 终端
│       ├── security/          # JWT 认证、RBAC 授权
│       └── model/             # entity / dto / vo / enums
├── dockerportal-web/          # Vue 3 前端：33 个页面
│   └── src/{api,views,components,composables,stores,utils}
├── Dockerfile                 # 多阶段构建：后端 + 前端 → 单镜像（非 root 运行）
└── docker-compose.yml         # 一键部署
```

| 层 | 选型 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.2.5（Web / Security / JPA / WebSocket / Validation / Cache）、H2 嵌入式数据库 |
| Docker | docker-java 3.3.6（httpclient5 transport）、MINA SSHD 2.12.1、JJWT 0.12.5、Caffeine 3.1.8 |
| 前端 | Vue 3.4、Vite 5、TypeScript、Element Plus 2.7、Pinia 2.1、ECharts 5.5、xterm.js 5.3、CodeMirror 6 |
| 交付 | 多阶段 Dockerfile → 单镜像，运行时基于 Eclipse Temurin 21 JRE Alpine，非 root 用户 |

后端不写死本机 Docker：所有操作都带 `envId`，由 `DockerClientManager` 按环境配置创建并复用客户端；`/system/df` 这类 docker-java 尚未封装的端点走底层 HTTP 调用。

## 开发指南

```bash
# 后端（JDK 21 + Maven）
cd dockerportal-server && mvn spring-boot:run     # API: http://localhost:9800/api

# 前端（Node 20+）
cd dockerportal-web && npm install && npm run dev  # http://localhost:3000，已配置代理到 9800
```

提交前请本地跑一遍构建，两条命令就是 CI 的最小集合：

```bash
cd dockerportal-server && mvn -q -o compile
cd dockerportal-web && npm run build               # vue-tsc 类型检查 + vite build
```

几条项目内约定：

- 前端直接消费 docker-java 返回的原生模型，字段是 **PascalCase**，不要在前端重命名成 camelCase
- 多行文本编辑（compose、Dockerfile、容器内文件）统一走 `CodeEditor`（CodeMirror 6）组件
- 面板级全屏统一使用 `useFullscreen` 组合式函数，不要各写一份样式
- API 遵循 RESTful：资源名复数、动作用子路径（如 `POST /containers/{id}/start`）

## 使用须知

几点提前说清，用起来会更顺手：

- 定位是**个人与小团队**，数据用 H2 单文件库，备份拷走整个数据目录即可；没有做多实例水平扩展。
- 告警的规则管理、事件历史、Webhook 发送链路都已完整，指标自动评估排在下一阶段（见路线图），需要时可以先用外部脚本调 `/api` 触发。
- 界面与文档目前只有中文，i18n 在路线图上。
- 集成测试与 e2e 正在补齐，改动核心链路前先备份一份数据目录，稳妥。
- 容器克隆、批量操作、资源配额与成本视图等能力还在规划中。

## 路线图

- [x] 多环境（socket / TCP / SSH）与凭据加密
- [x] Swarm 全链路：节点标签、服务、Stack、Secrets/Configs、任务
- [x] 容器终端、日志流、文件浏览编辑、资源热更新
- [x] 磁盘占用与分级 prune、审计日志、RBAC
- [ ] 告警指标自动评估（把现有 stats 采集接进 `AlertService.fireAlert`，入口小，很适合上手贡献）
- [ ] 批量操作与容器克隆
- [ ] 自动化测试（后端集成测试 + 前端 e2e）
- [ ] 英文界面与文档（i18n）
- [ ] OpenAPI 文档导出，方便脚本化调用

## 交流与反馈

**QQ 群：`1064928035`** —— 部署踩坑、使用问题、功能建议都可以聊。提问时带上 DockerPortal 版本、Docker 版本和完整现象（后端日志就在容器 stdout，`docker logs dockerportal` 即可拿到），能少走很多来回。

Bug 与需求建议同时开 [issue](https://github.com/liujun999999/DockerPortal/issues) 记录 —— 群聊内容检索不到，但对后来人很值钱；群里聊清楚后一般会整理成 issue 跟踪。

## 贡献

任何 PR 都欢迎，从小修小补到重构都行。建议流程：

1. 先开 issue 说清要解决的问题，尤其是涉及交互或数据结构改动的
2. 从 `main` 切分支，一个分支只做一件事
3. 提交信息用简洁中文说明「为什么改」，能关联 issue 就带上编号
4. 提 PR 前本地跑通上面两条构建命令

想挑一个入门任务：[good first issue](https://github.com/liujun999999/DockerPortal/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22)。

安全相关的漏洞请**不要**开公开 issue，也**不要**在群里问，直接私信维护者，我们会在修复后一并披露细节。

## 许可证

本项目以 [Apache License 2.0](LICENSE) 开源（[完整协议文本](https://www.apache.org/licenses/LICENSE-2.0)，仓库内 `LICENSE` 为官方原文，逐字未改）。

选型时我在 MIT 和 Apache-2.0 之间选了后者，理由很现实：这个面板的正常使用方式是**持有 docker.sock、拿着各环境的 SSH 凭据**，它注定会被改进、被接到企业内部工具里。Apache-2.0 在「允许闭源集成与商用」的同时多了两样东西：明确的**专利授权条款**（贡献者自动授予使用者专利许可，防止日后拿专利反咬），以及要求修改过的文件**标注变更**、保留 NOTICE。MIT 更短更宽松，但这两点在长期项目上是有价值的保险。

它不是 Copyleft：你基于它做一个商业发行版并不被迫开源。这个面板管理的是别人的服务器，而不是依赖某种许可证锁定，这是我有意的取舍。你只需要保留版权与许可证声明即可。

## 致谢

- [Docker](https://www.docker.com/) 与 [docker-java](https://github.com/docker-java/docker-java)
- [Spring Boot](https://spring.io/projects/spring-boot)、[Vue](https://vuejs.org/)、[Element Plus](https://element-plus.org/)、[ECharts](https://echarts.apache.org/)、[xterm.js](https://xtermjs.org/)、[CodeMirror](https://codemirror.net/)
- 所有提 issue、发 PR 和帮忙试用反馈的人

---

如果这个项目帮到了你，欢迎点个 **Star**、加群 **1064928035** 一起把它打磨得更好；总览页还有个打赏入口，扫码随意，不打赏也完全不影响任何功能。

<!-- 如需把版权挂到真实姓名或组名，改下面这行 -->
Copyright 2026 liujun999999（以及 DockerPortal 的贡献者）
