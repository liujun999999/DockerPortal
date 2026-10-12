package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.model.dto.ContainerCreateRequest;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContainerService {

    private final DockerClientManager dockerClientManager;
    private final ImageService imageService;

    public List<Container> listContainers(Long envId, boolean showAll) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listContainersCmd()
                .withShowAll(showAll)
                .exec();
    }

    public InspectContainerResponse inspectContainer(Long envId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.inspectContainerCmd(containerId).exec();
    }

    /**
     * 创建容器：覆盖 docker run 全部可配置项，前端传入的字段语义与 CLI/compose 一致。
     * 字段错误优先于环境不存在上报（先校验/组装再取 DockerClient），与 SwarmService 保持一致。
     */
    public String createContainer(Long envId, ContainerCreateRequest request) {
        DockerClient client = dockerClientManager.getClient(envId);

        // --pull：本地没有镜像时自动拉取（携带仓库凭据）
        if (Boolean.TRUE.equals(request.getPullIfMissing())) {
            ensureImage(envId, client, request);
        }

        var createCmd = client.createContainerCmd(request.getImage());
        var hostConfig = HostConfig.newHostConfig();

        // ===== 基础 =====
        if (isNotBlank(request.getName())) createCmd.withName(request.getName());
        if (isNotBlank(request.getHostname())) createCmd.withHostName(request.getHostname());
        if (isNotBlank(request.getDomainName())) createCmd.withDomainName(request.getDomainName());
        if (request.getLabels() != null && !request.getLabels().isEmpty()) createCmd.withLabels(request.getLabels());

        // ===== 启动 =====
        if (notEmpty(request.getEntrypoint())) createCmd.withEntrypoint(request.getEntrypoint());
        if (notEmpty(request.getCommand())) createCmd.withCmd(request.getCommand());
        if (notEmpty(request.getEnv())) createCmd.withEnv(request.getEnv());
        if (isNotBlank(request.getWorkingDir())) createCmd.withWorkingDir(request.getWorkingDir());
        if (isNotBlank(request.getUser())) createCmd.withUser(request.getUser());
        if (Boolean.TRUE.equals(request.getTty())) createCmd.withTty(true).withAttachStdout(true).withAttachStderr(true);
        if (Boolean.TRUE.equals(request.getStdinOpen())) createCmd.withStdinOpen(true).withAttachStdin(true);
        if (Boolean.TRUE.equals(request.getInit())) hostConfig.withInit(true);
        if (isNotBlank(request.getStopSignal())) createCmd.withStopSignal(request.getStopSignal());
        if (request.getStopTimeout() != null && request.getStopTimeout() > 0) createCmd.withStopTimeout(request.getStopTimeout());
        if (request.getHealthcheck() != null && notEmpty(request.getHealthcheck().getTest())) {
            var hc = request.getHealthcheck();
            var healthCheck = new HealthCheck().withTest(hc.getTest());
            if (hc.getIntervalSec() != null && hc.getIntervalSec() > 0) healthCheck.withInterval(hc.getIntervalSec() * 1_000_000_000L);
            if (hc.getTimeoutSec() != null && hc.getTimeoutSec() > 0) healthCheck.withTimeout(hc.getTimeoutSec() * 1_000_000_000L);
            if (hc.getStartPeriodSec() != null && hc.getStartPeriodSec() > 0) healthCheck.withStartPeriod(hc.getStartPeriodSec() * 1_000_000_000L);
            if (hc.getRetries() != null && hc.getRetries() > 0) healthCheck.withRetries(hc.getRetries());
            createCmd.withHealthcheck(healthCheck);
        }

        // ===== 网络 =====
        if (isNotBlank(request.getNetworkMode())) hostConfig.withNetworkMode(request.getNetworkMode());
        if (notEmpty(request.getNetworkAliases())) createCmd.withAliases(request.getNetworkAliases());
        if (notEmpty(request.getLinks())) {
            hostConfig.withLinks(request.getLinks().stream().map(Link::parse).collect(Collectors.toList()));
        }
        if (notEmpty(request.getExtraHosts())) hostConfig.withExtraHosts(request.getExtraHosts().toArray(new String[0]));
        if (notEmpty(request.getDns())) hostConfig.withDns(request.getDns());
        if (notEmpty(request.getDnsSearch())) hostConfig.withDnsSearch(request.getDnsSearch());
        if (isNotBlank(request.getMacAddress())) createCmd.withMacAddress(request.getMacAddress());

        // ===== 端口 =====
        if (Boolean.TRUE.equals(request.getPublishAll())) hostConfig.withPublishAllPorts(true);
        if (notEmpty(request.getExposedPorts())) {
            createCmd.withExposedPorts(request.getExposedPorts().stream()
                    .map(this::parseExposedPort).collect(Collectors.toList()));
        }
        if (notEmpty(request.getPortBindings())) {
            Ports portBindings = new Ports();
            List<ExposedPort> exposed = new ArrayList<>();
            for (var pb : request.getPortBindings()) {
                int containerPort = Integer.parseInt(requireNonBlank(pb.getContainerPort(), "容器端口不能为空"));
                ExposedPort ep = "udp".equalsIgnoreCase(pb.getProtocol())
                        ? ExposedPort.udp(containerPort) : ExposedPort.tcp(containerPort);
                exposed.add(ep);
                String hostIp = pb.getHostIp() == null ? "" : pb.getHostIp();
                if (isNotBlank(pb.getHostPortRangeEnd())) {
                    // -p 8000-9000:8000-9000 要求两端范围长度一致
                    int start = Integer.parseInt(requireNonBlank(pb.getHostPort(), "主机端口范围起始不能为空"));
                    int end = Integer.parseInt(pb.getHostPortRangeEnd());
                    portBindings.bind(ep, Ports.Binding.bindIpAndPortRange(hostIp, start, end));
                } else if (isNotBlank(pb.getHostPort())) {
                    portBindings.bind(ep, hostIp.isEmpty()
                            ? Ports.Binding.bindPort(Integer.parseInt(pb.getHostPort()))
                            : Ports.Binding.bindIpAndPort(hostIp, Integer.parseInt(pb.getHostPort())));
                } else {
                    // 只写容器端口 → 随机主机端口（-p 80）
                    portBindings.bind(ep, Ports.Binding.bindIp(""));
                }
            }
            // exposedPorts 与发布端口合并，避免覆盖
            var allExposed = new ArrayList<>(exposed);
            if (createCmd.getExposedPorts() != null) allExposed.addAll(Arrays.asList(createCmd.getExposedPorts()));
            createCmd.withExposedPorts(allExposed);
            hostConfig.withPortBindings(portBindings);
        }

        // ===== 存储 =====
        Map<String, String> tmpFs = new LinkedHashMap<>();
        if (notEmpty(request.getVolumeMounts())) {
            List<Mount> mounts = new ArrayList<>();
            for (var vm : request.getVolumeMounts()) {
                String type = vm.getType() == null ? "bind" : vm.getType().toLowerCase();
                if ("tmpfs".equals(type)) {
                    tmpFs.put(requireNonBlank(vm.getTarget(), "挂载目标路径不能为空"), "");
                    continue;
                }
                MountType mt = switch (type) {
                    case "volume" -> MountType.VOLUME;
                    default -> MountType.BIND;
                };
                mounts.add(new Mount()
                        .withType(mt)
                        .withSource(isNotBlank(vm.getSource()) ? vm.getSource() : null)
                        .withTarget(requireNonBlank(vm.getTarget(), "挂载目标路径不能为空"))
                        .withReadOnly(vm.isReadOnly()));
            }
            hostConfig.withMounts(mounts);
        }
        if (notEmpty(request.getTmpfs())) {
            request.getTmpfs().forEach(p -> tmpFs.put(requireNonBlank(p, "tmpfs 路径不能为空"), ""));
        }
        if (!tmpFs.isEmpty()) hostConfig.withTmpFs(tmpFs);

        // ===== 资源 =====
        if (request.getMemoryLimit() != null && request.getMemoryLimit() > 0) hostConfig.withMemory(request.getMemoryLimit());
        if (request.getMemorySwap() != null && request.getMemorySwap() > 0) hostConfig.withMemorySwap(request.getMemorySwap());
        if (request.getMemoryReservation() != null && request.getMemoryReservation() > 0) hostConfig.withMemoryReservation(request.getMemoryReservation());
        if (request.getCpuLimit() != null && request.getCpuLimit() > 0) hostConfig.withNanoCPUs(request.getCpuLimit());
        if (request.getCpuShares() != null && request.getCpuShares() > 0) hostConfig.withCpuShares(request.getCpuShares().intValue());
        if (isNotBlank(request.getCpusetCpus())) hostConfig.withCpusetCpus(request.getCpusetCpus());
        if (request.getShmSize() != null && request.getShmSize() > 0) hostConfig.withShmSize(request.getShmSize());
        if (request.getPidsLimit() != null && request.getPidsLimit() > 0) hostConfig.withPidsLimit(request.getPidsLimit());
        if (Boolean.TRUE.equals(request.getOomKillDisable())) hostConfig.withOomKillDisable(true);
        if (request.getOomScoreAdj() != null) hostConfig.withOomScoreAdj(request.getOomScoreAdj());
        if (notEmpty(request.getUlimits())) {
            hostConfig.withUlimits(request.getUlimits().stream()
                    .filter(u -> isNotBlank(u.getName()))
                    .map(u -> new Ulimit(u.getName(),
                            u.getSoft() == null ? -1 : u.getSoft(),
                            u.getHard() == null ? -1 : u.getHard()))
                    .collect(Collectors.toList()));
        }

        // ===== 安全 =====
        if (Boolean.TRUE.equals(request.getPrivileged())) hostConfig.withPrivileged(true);
        if (notEmpty(request.getCapAdd())) hostConfig.withCapAdd(toCapabilities(request.getCapAdd()).toArray(new Capability[0]));
        if (notEmpty(request.getCapDrop())) hostConfig.withCapDrop(toCapabilities(request.getCapDrop()).toArray(new Capability[0]));
        if (notEmpty(request.getSecurityOpts())) hostConfig.withSecurityOpts(request.getSecurityOpts());
        if (notEmpty(request.getGroupAdd())) hostConfig.withGroupAdd(request.getGroupAdd());
        if (request.getSysctls() != null && !request.getSysctls().isEmpty()) hostConfig.withSysctls(request.getSysctls());
        if (notEmpty(request.getDevices())) {
            hostConfig.withDevices(request.getDevices().stream()
                    .map(d -> new Device(d, d, "rwm"))
                    .collect(Collectors.toList()));
        }
        if (Boolean.TRUE.equals(request.getReadOnly())) hostConfig.withReadonlyRootfs(true);

        // ===== 生命周期 / 驱动 =====
        if (isNotBlank(request.getRestartPolicy())) {
            try {
                hostConfig.withRestartPolicy(RestartPolicy.parse(request.getRestartPolicy()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("restartPolicy 无效: " + request.getRestartPolicy()
                        + "（支持 no / always / unless-stopped / on-failure[:max]）");
            }
        }
        if (Boolean.TRUE.equals(request.getAutoRemove())) hostConfig.withAutoRemove(true);
        if (isNotBlank(request.getLogDriver())) {
            LogConfig.LoggingType type;
            try {
                type = LogConfig.LoggingType.valueOf(request.getLogDriver().toUpperCase().replace('-', '_'));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("logDriver 无效: " + request.getLogDriver());
            }
            var logConfig = new LogConfig(type);
            if (request.getLogOpts() != null && !request.getLogOpts().isEmpty()) logConfig.setConfig(new HashMap<>(request.getLogOpts()));
            hostConfig.withLogConfig(logConfig);
        }
        if (isNotBlank(request.getPidMode())) hostConfig.withPidMode(request.getPidMode());
        if (isNotBlank(request.getIpcMode())) hostConfig.withIpcMode(request.getIpcMode());
        if (isNotBlank(request.getUtsMode())) hostConfig.withUtSMode(request.getUtsMode());
        if (isNotBlank(request.getUsernsMode())) hostConfig.withUsernsMode(request.getUsernsMode());
        if (isNotBlank(request.getCgroupParent())) hostConfig.withCgroupParent(request.getCgroupParent());
        if (isNotBlank(request.getRuntime())) hostConfig.withRuntime(request.getRuntime());

        createCmd.withHostConfig(hostConfig);

        CreateContainerResponse response = createCmd.exec();
        log.info("Container created: {} ({})", request.getImage(), response.getId());
        return response.getId();
    }

    /** 本地已有该镜像则跳过；否则按 registryId（或智能匹配）拉取 */
    private void ensureImage(Long envId, DockerClient client, ContainerCreateRequest request) {
        try {
            client.inspectImageCmd(request.getImage()).exec();
            return;
        } catch (NotFoundException ignored) {
            // 本地不存在，继续拉取
        }
        String[] nt = splitImageTag(request.getImage());
        imageService.pullImage(envId, nt[0], nt[1], request.getRegistryId());
    }

    /** nginx:1.25 → [nginx, 1.25]；registry.example.com:5000/app → [registry.example.com:5000/app, latest] */
    static String[] splitImageTag(String image) {
        int idx = image.lastIndexOf(':');
        if (idx > 0 && !image.substring(idx + 1).contains("/")) {
            return new String[]{image.substring(0, idx), image.substring(idx + 1)};
        }
        return new String[]{image, "latest"};
    }

    private ExposedPort parseExposedPort(String spec) {
        // 支持 8080 / 8080/tcp / 8080/udp
        String s = spec.trim();
        int slash = s.indexOf('/');
        int port = Integer.parseInt(slash > 0 ? s.substring(0, slash) : s);
        return "udp".equalsIgnoreCase(slash > 0 ? s.substring(slash + 1) : "tcp")
                ? ExposedPort.udp(port) : ExposedPort.tcp(port);
    }

    /** SYS_ADMIN / sys_admin / CAP_SYS_ADMIN 统一转成 Capability 枚举 */
    private List<Capability> toCapabilities(List<String> names) {
        List<Capability> caps = new ArrayList<>();
        for (String n : names) {
            String up = n.trim().toUpperCase();
            if (up.startsWith("CAP_")) up = up.substring(4);
            try {
                caps.add(Capability.valueOf(up));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("capability 无效: " + n);
            }
        }
        return caps;
    }

    private static boolean isNotBlank(String s) { return s != null && !s.isBlank(); }
    private static boolean notEmpty(List<?> l) { return l != null && !l.isEmpty(); }
    private static String requireNonBlank(String s, String msg) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(msg);
        return s;
    }

    public void startContainer(Long envId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.startContainerCmd(containerId).exec();
        log.info("Container started: {}", containerId);
    }

    public void stopContainer(Long envId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.stopContainerCmd(containerId).exec();
        log.info("Container stopped: {}", containerId);
    }

    public void restartContainer(Long envId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.restartContainerCmd(containerId).exec();
        log.info("Container restarted: {}", containerId);
    }

    public void pauseContainer(Long envId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.pauseContainerCmd(containerId).exec();
        log.info("Container paused: {}", containerId);
    }

    public void unpauseContainer(Long envId, String containerId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.unpauseContainerCmd(containerId).exec();
        log.info("Container unpaused: {}", containerId);
    }

    public void removeContainer(Long envId, String containerId, boolean force) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.removeContainerCmd(containerId)
                .withForce(force)
                .exec();
        log.info("Container removed: {}", containerId);
    }

    public void renameContainer(Long envId, String containerId, String newName) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.renameContainerCmd(containerId)
                .withName(newName)
                .exec();
        log.info("Container renamed: {} -> {}", containerId, newName);
    }

    /**
     * docker update：运行中/已停止容器均可热调资源限制，字段留空不修改。
     * 入参：memoryMb/memorySwapMb/memoryReservationMb（MB）、cpuCores（核数，支持小数，换算 quota/period）、cpuShares、cpusetCpus。
     */
    public void updateResources(Long envId, String containerId, Map<String, Object> body) {
        DockerClient client = dockerClientManager.getClient(envId);
        var cmd = client.updateContainerCmd(containerId);
        boolean any = false;
        Long mem = mbToBytes(body.get("memoryMb"));
        if (mem != null) { cmd.withMemory(mem); any = true; }
        Long swap = mbToBytes(body.get("memorySwapMb"));
        if (swap != null) { cmd.withMemorySwap(swap); any = true; }
        Long res = mbToBytes(body.get("memoryReservationMb"));
        if (res != null) { cmd.withMemoryReservation(res); any = true; }
        // docker update 的硬约束：memory 与 memorySwap 必须同时提交，否则 daemon 409
        // （如已存在 swap 限额小于新 memory 时）；缺省项用当前值补齐，改 memory 时保持 swap 差值
        if ((mem != null || swap != null) && ((body.get("memorySwapMb") == null && mem != null) || (swap != null && body.get("memoryMb") == null))) {
            var hc = client.inspectContainerCmd(containerId).exec().getHostConfig();
            long curMem = hc.getMemory() == null ? 0 : hc.getMemory();
            long curSwap = hc.getMemorySwap() == null ? 0 : hc.getMemorySwap();
            if (mem != null && swap == null) {
                // memorySwap 是“内存+swap 总限额”：无限保持无限，有限额则保持原有 swap 额度不变
                cmd.withMemorySwap(curSwap <= 0 || curSwap < curMem ? mem : mem + (curSwap - curMem));
            } else if (swap != null && mem == null) {
                cmd.withMemory(curMem); // 仅改 swap 时回填当前 memory
            }
        }
        Object cores = body.get("cpuCores");
        if (cores != null && !String.valueOf(cores).isBlank()) {
            double c;
            try { c = Double.parseDouble(String.valueOf(cores)); } catch (NumberFormatException e) {
                throw new IllegalArgumentException("cpuCores 不是合法数字: " + cores);
            }
            if (c <= 0) throw new IllegalArgumentException("cpuCores 必须大于 0");
            // 100ms 调度周期，quota 按核数比例；最小 quota 1ms 微秒级
            cmd.withCpuPeriod(100000).withCpuQuota((int) Math.max(1000, Math.round(c * 100000)));
            any = true;
        }
        Object shares = body.get("cpuShares");
        if (shares != null && !String.valueOf(shares).isBlank()) {
            cmd.withCpuShares(Integer.parseInt(String.valueOf(shares).trim()));
            any = true;
        }
        Object cpuset = body.get("cpusetCpus");
        if (cpuset != null && !String.valueOf(cpuset).isBlank()) {
            cmd.withCpusetCpus(String.valueOf(cpuset).trim());
            any = true;
        }
        if (!any) throw new IllegalArgumentException("请至少填写一项要调整的资源限制");
        cmd.exec();
        log.info("Container resources updated: {} {}", containerId, body);
    }

    private Long mbToBytes(Object mb) {
        if (mb == null || String.valueOf(mb).isBlank()) return null;
        double v = Double.parseDouble(String.valueOf(mb).trim());
        if (v < 0) throw new IllegalArgumentException("内存限制不能为负");
        return v == 0 ? 0L : Math.round(v * 1024 * 1024);
    }

    public String getContainerLogs(Long envId, String containerId, int tail, boolean timestamps) {
        DockerClient client = dockerClientManager.getClient(envId);
        StringBuilder logs = new StringBuilder();
        try {
            client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tail)
                    .withTimestamps(timestamps)
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame frame) {
                            logs.append(new String(frame.getPayload()));
                        }
                    })
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return logs.toString();
    }
}
