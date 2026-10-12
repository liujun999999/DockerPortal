package com.dockerportal.websocket;

import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.entity.SshConfig;
import com.dockerportal.service.AuditService;
import com.dockerportal.service.SshService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.channel.PtyCapableChannelSession;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * SSH 终端 WebSocket 处理器
 * <p>
 * 两种模式：
 * <ul>
 *   <li>普通模式：登录后得到宿主机交互 shell</li>
 *   <li>exec 模式（URL 带 {@code exec=<容器ID>}）：直接执行 docker exec 进入容器 shell，
 *       用于进入跑在其他 Swarm 节点上的副本（manager 本地没有该容器，无法直连 daemon）</li>
 * </ul>
 * 帧协议与容器终端保持一致：二进制帧 = 键盘输入，文本帧 = JSON 控制指令（resize）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshTerminalHandler extends AbstractWebSocketHandler {

    private static final CloseStatus STATUS_NO_CONFIG =
            new CloseStatus(4001, "目标主机尚未配置 SSH 连接信息，请先完成配置");

    /** 容器标识会被拼进远端命令行，只允许安全字符 */
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9_.-]{1,128}$");
    private static final Set<String> ALLOWED_SHELLS =
            Set.of("sh", "bash", "dash", "ash", "zsh", "fish");

    private final SshService sshService;
    private final WsAuthChecker wsAuthChecker;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();
    /** shell 与 exec 两种通道的共同父类，支持 PTY 与 sendWindowChange */
    private final Map<String, PtyCapableChannelSession> channels = new ConcurrentHashMap<>();
    private final Map<String, SshClient> clients = new ConcurrentHashMap<>();
    /** 会话元数据，关闭时补写审计（含时长） */
    private final Map<String, SessionTrack> tracked = new ConcurrentHashMap<>();

    private record SessionTrack(String username, Long envId, String action, String resourceType,
                                String resourceId, String detail, String ip, long startedAt) {}

    @Override
    public void afterConnectionEstablished(WebSocketSession wsSession) {
        String target = null;
        String hostAddr = null;
        SshClient sshClient = null;
        try {
            // SSH = 宿主机级 shell，仅 ADMIN
            String role = wsAuthChecker.resolveRole(wsSession);
            if (wsAuthChecker.closeIfUnauthorized(wsSession, role, "ADMIN")) {
                return;
            }
            var params = extractParams(wsSession);
            Long envId = Long.parseLong(params.get("envId"));
            target = params.get("target");
            Map<String, String> query = parseQuery(wsSession);
            // exec=<容器ID> 表示要直接进容器 shell（Swarm 副本场景），否则是普通宿主 shell
            String execCommand = buildExecCommand(query.get("exec"), query.get("shell"), query.get("sudo"));

            // 纯数字 = SSH 配置 ID（支持自定义主机）；否则为 Swarm 节点 ID（兼容旧路径）
            SshConfig config = target.matches("\\d+")
                    ? sshService.getConfigForConnect(envId, Long.parseLong(target))
                    : sshService.getConfigByNode(envId, target);
            if (config == null) {
                closeQuietly(wsSession, STATUS_NO_CONFIG);
                return;
            }
            hostAddr = config.getHost() + ":" + config.getPort();
            int[] size = resolveTerminalSize(query);

            sshClient = SshClient.setUpDefaultClient();
            sshClient.start();
            ClientSession session = sshClient
                    .connect(config.getUsername(), config.getHost(), config.getPort())
                    .verify(10000)
                    .getSession();
            sessions.put(wsSession.getId(), session);
            clients.put(wsSession.getId(), sshClient);

            authenticate(session, config);

            PtyCapableChannelSession channel;
            if (execCommand == null) {
                channel = session.createShellChannel();
            } else {
                ChannelExec execChannel = session.createExecChannel(execCommand);
                // 坑：ChannelExec 构造时 wantPty=false（ChannelShell 是 true），不发 pty-req 会让
                // docker exec -t 立刻报 "the input device is not a TTY" 并退出
                execChannel.setUsePty(true);
                channel = execChannel;
            }
            // 远端 stderr 是独立流，不合并到 stdout 就永远读不到，报错在前端表现为一片空白
            channel.setRedirectErrorStream(true);
            // sshd 默认 PTY 类型是 "dummy"，远端会设 TERM=dumb 导致 shell 禁用行编辑器、退格失效
            channel.setPtyType("xterm-256color");
            channel.setPtyColumns(size[0]);
            channel.setPtyLines(size[1]);
            channel.open().verify(10000);
            channels.put(wsSession.getId(), channel);

            // 客户端通道：getInvertedOut() 读远端输出，getInvertedIn() 写远端输入；
            // getIn()/getOut() 是用户自行 set 的槽位，默认 null，不可直接使用
            startOutputPump(channel, wsSession, target, execCommand != null);

            trackSession(wsSession, envId, target, config.getUsername(), hostAddr, query.get("exec"));

            log.info("SSH session established for target {} ({}@:{}) exec={}", target, config.getUsername(),
                    config.getHost(), config.getPort(), execCommand != null);
        } catch (BusinessException e) {
            closeQuietly(wsSession, new CloseStatus(4002, e.getMessage()));
        } catch (Exception e) {
            log.error("SSH terminal failed for target {}", target, e);
            closeQuietly(wsSession, classify(e, hostAddr));
        }
    }

    /**
     * 把远端输出回推到浏览器；远端命令退出（如输入 exit）后必须关掉 WS，
     * 否则前端标签看似存活但输入已无人响应
     */
    private void startOutputPump(PtyCapableChannelSession channel, WebSocketSession wsSession,
                                 String target, boolean execMode) {
        InputStream channelOut = channel.getInvertedOut();
        Thread.ofVirtual().name("ssh-output-" + wsSession.getId()).start(() -> {
            byte[] buffer = new byte[4096];
            try {
                int len;
                while ((len = channelOut.read(buffer)) != -1) {
                    if (!wsSession.isOpen()) break;
                    synchronized (wsSession) {
                        wsSession.sendMessage(new BinaryMessage(Arrays.copyOf(buffer, len)));
                    }
                }
            } catch (IOException e) {
                log.debug("SSH output stream closed for target {}", target);
            } finally {
                if (execMode) {
                    reportExitStatus(channel, wsSession);
                }
                if (wsSession.isOpen()) {
                    closeQuietly(wsSession, CloseStatus.NORMAL.withReason("远端会话已结束"));
                }
            }
        });
    }

    /** exec 通道非零退出时把退出码写回终端，避免只剩一个空白终端让人无从判断 */
    private void reportExitStatus(PtyCapableChannelSession channel, WebSocketSession wsSession) {
        try {
            // exit-status 可能略晚于 stdout 的 EOF，短暂等一下以免误报成"未知"
            channel.waitFor(EnumSet.of(ClientChannelEvent.EXIT_STATUS), 1500L);
            Integer status = channel.getExitStatus();
            if (status == null || status == 0 || !wsSession.isOpen()) {
                return;
            }
            // 容器内正常 exit 已会回退到宿主 shell，走到这里说明是宿主 shell 自起失败或链路异常
            String hint = "\r\n[远端命令退出码 " + status
                    + "；若为 127 通常是登录 shell 启动失败或远端 PATH 里找不到 docker]\r\n";
            synchronized (wsSession) {
                wsSession.sendMessage(new BinaryMessage(hint.getBytes(StandardCharsets.UTF_8)));
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Failed to report exec exit status: {}", e.getMessage());
        }
    }

    /**
     * 组装 docker exec 命令。无 exec 参数时返回 null（走普通宿主 shell）。
     * 容器标识与 shell 均做校验，避免拼进远端命令行时被注入。
     */
    private String buildExecCommand(String containerRef, String shell, String sudoFlag) {
        if (containerRef == null || containerRef.isBlank()) {
            return null;
        }
        String cid = containerRef.trim();
        if (!SAFE_ID.matcher(cid).matches()) {
            throw new BusinessException(40010, "非法的容器标识");
        }
        boolean sudo = "1".equals(sudoFlag) || "true".equalsIgnoreCase(sudoFlag);
        String prefix = sudo ? "sudo -n " : "";
        String inner;
        if (shell != null && !shell.isBlank()) {
            String s = shell.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_SHELLS.contains(s)) {
                throw new BusinessException(40010, "不支持的 shell：" + s);
            }
            inner = prefix + "docker exec -i -t " + cid + " " + s;
        } else {
            // 未指定则优先 bash（补全/历史更完整），镜像里没有 bash 时自动退回 sh
            inner = prefix + "docker exec -i -t " + cid
                    + " sh -c \"command -v bash >/dev/null 2>&1 && exec bash || exec sh\"";
        }
        return wrapWithLoginShell(inner);
    }

    /**
     * 用登录 shell 包一层，并让容器退出后回退到宿主机交互 shell（而不是直接关掉会话）。
     * <p>
     * 两层考虑：
     * <ul>
     *   <li>sshd 处理 exec 请求的方式是 {@code <用户 shell> -c <命令>}，属于非交互、非登录 shell：
     *       zsh 只 source {@code ~/.zshenv}，而 Homebrew 这类工具链的 PATH 写在 {@code ~/.zprofile}，
     *       于是手动 ssh 能敲 docker、程序化 exec 却报 command not found（127）。走 -lc 与手动登录对齐。</li>
     *   <li>容器内 {@code exit} 不带参数时，bash 用最后一条命令的退出码作为自己的退出码（可能非 0），
     *       直接结束会话会把一次正常退出渲染成故障；接上宿主 shell 更符合“逐层退出”的直觉。</li>
     * </ul>
     * 内层命令只含双引号、不含单引号（容器标识已过 {@link #SAFE_ID} 校验，shell 走白名单），
     * 可以安全地放进单引号；其中的 {@code $?} 由登录 shell 自己展开。
     */
    private String wrapWithLoginShell(String command) {
        return "\"${SHELL:-sh}\" -lc '"
                + command + "; __rc=$?; echo;"
                + " echo \"[容器会话结束（退出码 ${__rc}），已返回宿主机 shell，输入 exit 关闭本标签]\";"
                + " exec \"${SHELL:-sh}\" -i'";
    }

    /** 记录终端会话审计：打开一条，关闭时再补一条带时长的记录 */
    private void trackSession(WebSocketSession wsSession, Long envId, String target,
                              String sshUser, String hostAddr, String execContainer) {
        String username = wsAuthChecker.resolveUsername(wsSession);
        String ip = wsSession.getRemoteAddress() == null ? null : wsSession.getRemoteAddress().toString();
        boolean execMode = execContainer != null && !execContainer.isBlank();
        String action = execMode ? "CONTAINER_TERMINAL_OPEN" : "SSH_TERMINAL_OPEN";
        String resourceType = execMode ? "container" : "ssh-session";
        String detail = (execMode ? "进入容器终端 " + execContainer : "登录宿主终端")
                + "（经 " + sshUser + "@" + hostAddr + "）";
        tracked.put(wsSession.getId(), new SessionTrack(username, envId, action, resourceType,
                target, detail, ip, System.currentTimeMillis()));
        auditService.recordByUsername(username, action, resourceType, target, envId, detail, ip);
    }

    /**
     * 把 sshd 抛出的底层异常映射成用户能看懂的原因
     */
    private CloseStatus classify(Exception e, String target) {
        String detail = rootMessage(e);
        String lower = detail.toLowerCase();
        String at = target == null ? "" : "（" + target + "）";
        if (lower.contains("refused") || lower.contains("unknown host") || lower.contains("no route")
                || lower.contains("network") || lower.contains("unreachable")) {
            return new CloseStatus(4003, "无法连接到目标主机" + at + "：" + detail);
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return new CloseStatus(4003, "连接超时，请确认主机地址与端口可达" + at);
        }
        if (lower.contains("auth") || lower.contains("password") || lower.contains("publickey")
                || lower.contains("none supported") || e instanceof org.apache.sshd.common.SshException) {
            return new CloseStatus(4002, "SSH 认证失败，请检查用户名与密码（或私钥）：" + detail);
        }
        return new CloseStatus(4000, "连接失败：" + detail);
    }

    private void authenticate(ClientSession session, SshConfig config)
            throws IOException, java.security.GeneralSecurityException {
        switch (config.getAuthType()) {
            case PASSWORD -> {
                if (config.getPassword() == null || config.getPassword().isBlank()) {
                    throw new BusinessException(40002, "SSH 密码未配置，请重新保存配置");
                }
                session.addPasswordIdentity(config.getPassword());
            }
            case KEY -> {
                String privateKey = config.getPrivateKey();
                if (privateKey == null || privateKey.isBlank()) {
                    throw new BusinessException(40002, "SSH 私钥未配置，请重新保存配置");
                }
                FilePasswordProvider pw = (sess, resource, attemptNo) -> null;
                Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(session,
                        null, new ByteArrayInputStream(privateKey.getBytes(StandardCharsets.UTF_8)), pw);
                int count = 0;
                for (KeyPair kp : keys) {
                    session.addPublicKeyIdentity(kp);
                    count++;
                }
                if (count == 0) {
                    throw new BusinessException(40002, "私钥无法解析，仅支持 OpenSSH PEM 格式私钥");
                }
            }
        }
        session.auth().verify(10000);
        if (!session.isAuthenticated()) {
            throw new BusinessException(40002, "SSH 认证失败，请检查用户名与密码（或私钥）");
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession wsSession, BinaryMessage message) {
        PtyCapableChannelSession channel = channels.get(wsSession.getId());
        if (channel == null) return;
        try {
            ByteBuffer payload = message.getPayload();
            byte[] bytes = new byte[payload.remaining()];
            payload.get(bytes);
            writeStdin(channel, bytes);
        } catch (Exception e) {
            log.warn("Failed to forward SSH input: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession wsSession, TextMessage message) {
        PtyCapableChannelSession channel = channels.get(wsSession.getId());
        if (channel == null) return;
        String payload = message.getPayload();
        try {
            if (payload.startsWith("{")) {
                JsonNode node = objectMapper.readTree(payload);
                if ("resize".equals(node.path("type").asText())) {
                    int cols = node.path("cols").asInt(0);
                    int rows = node.path("rows").asInt(0);
                    if (cols > 0 && rows > 0) {
                        channel.sendWindowChange(cols, rows);
                    }
                    return;
                }
            }
        } catch (IOException e) {
            log.debug("Not a control frame, treat as input");
        }
        // 兼容旧客户端：非控制帧文本直接写入 stdin
        writeStdin(channel, payload.getBytes(StandardCharsets.UTF_8));
    }

    private void writeStdin(PtyCapableChannelSession channel, byte[] bytes) {
        try {
            OutputStream stdin = channel.getInvertedIn();
            stdin.write(bytes);
            stdin.flush();
        } catch (IOException e) {
            log.debug("SSH stdin closed: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession wsSession, CloseStatus status) {
        String id = wsSession.getId();
        PtyCapableChannelSession channel = channels.remove(id);
        ClientSession session = sessions.remove(id);
        SshClient client = clients.remove(id);
        SessionTrack track = tracked.remove(id);
        if (track != null) {
            long seconds = (System.currentTimeMillis() - track.startedAt()) / 1000;
            auditService.recordByUsername(track.username(),
                    track.action().replace("_OPEN", "_CLOSE"), track.resourceType(), track.resourceId(),
                    track.envId(), track.detail() + "，会话时长 " + seconds + "s", track.ip());
        }
        try {
            if (channel != null) channel.close();
            if (session != null) session.close(true);
        } catch (IOException e) {
            log.debug("Error closing SSH session: {}", e.getMessage());
        }
        try {
            if (client != null) client.stop();
        } catch (Exception e) {
            log.debug("Error stopping SSH client: {}", e.getMessage());
        }
    }

    /**
     * 解析握手 URL 上的查询参数（token/cols/rows/exec/shell/sudo）
     */
    private Map<String, String> parseQuery(WebSocketSession wsSession) {
        Map<String, String> map = new HashMap<>();
        URI uri = wsSession.getUri();
        String query = uri == null ? null : uri.getQuery();
        if (query == null) return map;
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            if (idx <= 0) continue;
            map.put(pair.substring(0, idx),
                    URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    /**
     * 初始终端尺寸：优先取握手 URL 上的 cols/rows，缺省 120x30
     */
    private int[] resolveTerminalSize(Map<String, String> query) {
        int cols = 120;
        int rows = 30;
        try {
            cols = Integer.parseInt(query.getOrDefault("cols", ""));
        } catch (NumberFormatException ignored) {
        }
        try {
            rows = Integer.parseInt(query.getOrDefault("rows", ""));
        } catch (NumberFormatException ignored) {
        }
        return new int[]{Math.min(Math.max(cols, 20), 500), Math.min(Math.max(rows, 5), 200)};
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.getClass().getSimpleName();
    }

    private void closeQuietly(WebSocketSession wsSession, CloseStatus status) {
        try {
            wsSession.close(status);
        } catch (IOException e) {
            log.debug("Failed to close ws session: {}", e.getMessage());
        }
    }

    private Map<String, String> extractParams(WebSocketSession session) {
        String path = session.getUri().getPath();
        UriTemplate template = new UriTemplate("/ws/env/{envId}/ssh/terminal/{target}");
        return template.match(path);
    }
}
