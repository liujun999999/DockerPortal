package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.exception.BusinessException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.ContainerSpec;
import com.github.dockerjava.api.model.EndpointResolutionMode;
import com.github.dockerjava.api.model.EndpointSpec;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.Mount;
import com.github.dockerjava.api.model.MountType;
import com.github.dockerjava.api.model.NetworkAttachmentConfig;
import com.github.dockerjava.api.model.PortConfig;
import com.github.dockerjava.api.model.PortConfigProtocol;
import com.github.dockerjava.api.model.ResourceRequirements;
import com.github.dockerjava.api.model.ResourceSpecs;
import com.github.dockerjava.api.model.ServiceGlobalModeOptions;
import com.github.dockerjava.api.model.ServiceModeConfig;
import com.github.dockerjava.api.model.ServicePlacement;
import com.github.dockerjava.api.model.ServiceReplicatedModeOptions;
import com.github.dockerjava.api.model.ServiceRestartCondition;
import com.github.dockerjava.api.model.ServiceRestartPolicy;
import com.github.dockerjava.api.model.ServiceSpec;
import com.github.dockerjava.api.model.SwarmNode;
import com.github.dockerjava.api.model.SwarmNodeAvailability;
import com.github.dockerjava.api.model.Task;
import com.github.dockerjava.api.model.TaskSpec;
import com.github.dockerjava.api.model.UpdateConfig;
import com.github.dockerjava.api.model.UpdateFailureAction;
import com.github.dockerjava.api.model.UpdateOrder;
import com.github.dockerjava.api.model.LocalNodeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SwarmService {

    private final DockerClientManager dockerClientManager;
    private final RegistryService registryService;
    private final SshService sshService;

    /**
     * 初始化 Swarm 集群（docker swarm init）。
     * advertiseAddr 为空时由 Docker 自动探测。
     */
    public void initSwarm(Long envId, String advertiseAddr) {
        DockerClient client = dockerClientManager.getClient(envId);
        var info = client.infoCmd().exec();
        var state = info.getSwarm() == null ? null : info.getSwarm().getLocalNodeState();
        if (state == LocalNodeState.ACTIVE || state == LocalNodeState.PENDING) {
            throw new BusinessException(40900, "当前环境已处于 Swarm 集群中，无需重复初始化");
        }
        var cmd = client.initializeSwarmCmd(new com.github.dockerjava.api.model.SwarmSpec().withName("default"));
        if (advertiseAddr != null && !advertiseAddr.isBlank()) {
            cmd.withAdvertiseAddr(advertiseAddr.trim());
        }
        cmd.exec();
        log.info("Swarm initialized on env {}, advertiseAddr={}", envId, advertiseAddr);
    }

    public void leaveSwarm(Long envId, boolean force) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.leaveSwarmCmd().withForceEnabled(force).exec();
        log.info("Left swarm on env {} (force={})", envId, force);
    }

    public Map<String, Object> getSwarmInfo(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        var info = client.infoCmd().exec();
        Map<String, Object> result = new LinkedHashMap<>();
        boolean swarmMode = info.getSwarm() != null
                && info.getSwarm().getLocalNodeState() != null
                && info.getSwarm().getLocalNodeState() != LocalNodeState.INACTIVE;
        result.put("swarmMode", swarmMode);
        if (swarmMode) {
            var swarm = client.infoCmd().exec().getSwarm();
            result.put("nodeId", swarm.getNodeID());
            result.put("nodeAddr", swarm.getNodeAddr());
            result.put("localNodeState", swarm.getLocalNodeState());
            result.put("controlAvailable", swarm.getControlAvailable());
            result.put("managers", swarm.getManagers());
            result.put("nodes", swarm.getNodes());
            result.put("clusterInfo", swarm.getClusterInfo());
        }
        return result;
    }

    public Map<String, Object> getJoinInfo(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        var infoSwarm = client.infoCmd().exec().getSwarm();
        Boolean control = infoSwarm == null ? null : infoSwarm.getControlAvailable();
        if (control != null && !control) {
            throw new BusinessException(40301, "当前节点不是 Swarm Manager，无法获取加入令牌");
        }
        var tokens = client.inspectSwarmCmd().exec().getJoinTokens();
        String addr = infoSwarm == null ? null : infoSwarm.getNodeAddr();
        if (addr == null || addr.isBlank()) addr = client.infoCmd().exec().getName();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workerToken", tokens == null ? null : tokens.getWorker());
        result.put("managerToken", tokens == null ? null : tokens.getManager());
        result.put("joinAddr", addr == null ? "<节点IP>" : addr + ":2377");
        return result;
    }

    public List<SwarmNode> listNodes(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listSwarmNodesCmd().exec();
    }

    public SwarmNode inspectNode(Long envId, String nodeId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listSwarmNodesCmd().exec().stream()
                .filter(n -> nodeId.equals(n.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(40400, "节点不存在: " + nodeId));
    }

    /**
     * 更新节点的标签与可用性。两者都是可选的，传 null 表示保持原值。
     * 注意标签是整体替换而非合并：swarmkit 处理 NodeUpdate 时是直接
     * {@code node.Spec = request.Spec.Copy()}，没带上 key 就等于删掉，所以调用方必须提交
     * 期望的完整标签集合（与服务/卷那种「空值即删除」的合并语义不同）。
     */
    public void updateNode(Long envId, String nodeId, Map<String, String> labels, String availability) {
        DockerClient client = dockerClientManager.getClient(envId);
        SwarmNode node = inspectNode(envId, nodeId);
        var nodeSpec = node.getSpec();
        if (nodeSpec == null) {
            throw new BusinessException(40900, "节点 " + nodeId + " 缺少 Spec 信息，无法更新");
        }
        if (labels != null) nodeSpec.withLabels(labels);
        if (availability != null) nodeSpec.withAvailability(SwarmNodeAvailability.valueOf(availability.toUpperCase()));
        client.updateSwarmNodeCmd()
                .withSwarmNodeId(nodeId)
                .withSwarmNodeSpec(nodeSpec)
                .withVersion(node.getVersion().getIndex())
                .exec();
        log.info("Node updated: {} labels={} availability={}", nodeId,
                labels == null ? "<未改动>" : labels.keySet(), availability);
    }

    /**
     * 节点详情里的「本节点任务」：docker-java 的 listTasksCmd 不支持按节点过滤，只能取回后自己筛。
     * 保留全部状态（含 done/rejected），详情页要按状态统计这个节点跑过什么。
     */
    public List<Task> listNodeTasks(Long envId, String nodeId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listTasksCmd().exec().stream()
                .filter(t -> nodeId.equals(t.getNodeId()))
                .toList();
    }

    public void removeNode(Long envId, String nodeId, boolean force) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.removeSwarmNodeCmd(nodeId).withForce(force).exec();
        log.info("Node removed: {}", nodeId);
    }

    public List<com.github.dockerjava.api.model.Service> listServices(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listServicesCmd().exec();
    }

    public com.github.dockerjava.api.model.Service inspectService(Long envId, String serviceId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.inspectServiceCmd(serviceId).exec();
    }

    /**
     * 创建服务：payload 覆盖 compose services.&lt;name&gt; 常用字段。
     * 参见 buildServiceSpec 支持的字段清单。
     */
    public String createService(Long envId, Map<String, Object> serviceDef) {
        // 先校验 payload 并拼装 spec，字段错误优先于环境不存在报错上报，便于前端定位问题
        ServiceSpec spec = buildServiceSpec(serviceDef);
        DockerClient client = dockerClientManager.getClient(envId);

        var cmd = client.createServiceCmd(spec);
        AuthConfig auth = resolveCreateAuth(envId, serviceDef);
        if (auth != null) cmd.withAuthConfig(auth);

        var response = cmd.exec();
        log.info("Service created: {} ({})", spec.getName(), response.getId());
        return response.getId();
    }

    /**
     * 前端 registryId=0 表示内置 Docker Hub 匿名；未传或 null 时按 image 主机自动匹配已保存凭据。
     */
    private AuthConfig resolveCreateAuth(Long envId, Map<String, Object> serviceDef) {
        Object rid = serviceDef.get("registryId");
        String image = str(serviceDef.get("image"));
        if (rid instanceof Number n) {
            long id = n.longValue();
            if (id == RegistryService.DOCKER_HUB_BUILTIN_ID) return null;
            return registryService.buildAuthConfigById(id);
        }
        // 未显式选择仓库：按镜像 host 自动命中已配置凭据（保留原有智能匹配）
        if (image != null && !image.isBlank()) return registryService.resolveAuthConfig(envId, image);
        return null;
    }

    /**
     * 把 compose 风格 payload 转成 docker-java 的 ServiceSpec。
     * 支持字段：name / image / labels / mode / replicas / entrypoint / command / env /
     * workDir / user / hostname / tty / readOnly / stopGracePeriod / stopSignal / healthcheck /
     * mounts / secrets / configs / networks / ports / endpointMode / resources / restart / placement / update / rollback。
     */
    ServiceSpec buildServiceSpec(Map<String, Object> d) {
        String image = str(d.get("image"));
        if (image == null || image.isBlank()) throw new BusinessException(40000, "镜像不能为空");

        // -------- ContainerSpec --------
        ContainerSpec cs = new ContainerSpec().withImage(image.trim());
        List<String> entrypoint = strList(d.get("entrypoint"));
        if (!entrypoint.isEmpty()) cs.withCommand(entrypoint);      // Swarm Command ≈ Docker Entrypoint
        List<String> command = strList(d.get("command"));
        if (!command.isEmpty()) cs.withArgs(command);               // Swarm Args ≈ Docker Command
        List<String> env = envList(d.get("env"));
        if (!env.isEmpty()) cs.withEnv(env);
        String wd = str(d.get("workDir"));
        if (wd != null && !wd.isBlank()) cs.withDir(wd.trim());
        String user = str(d.get("user"));
        if (user != null && !user.isBlank()) cs.withUser(user.trim());
        String hostname = str(d.get("hostname"));
        if (hostname != null && !hostname.isBlank()) cs.withHostname(hostname.trim());
        if (d.get("tty") instanceof Boolean b) cs.withTty(b);
        if (d.get("readOnly") instanceof Boolean b) cs.withReadOnly(b);
        Long graceNs = secToNs(d.get("stopGracePeriod"));
        if (graceNs != null) cs.withStopGracePeriod(graceNs);
        String stopSignal = str(d.get("stopSignal"));
        if (stopSignal != null && !stopSignal.isBlank()) cs.withStopSignal(stopSignal.trim());
        HealthCheck hc = buildHealth(d.get("healthcheck"));
        if (hc != null) cs.withHealthCheck(hc);
        List<Mount> mounts = buildMounts(d.get("mounts"));
        if (!mounts.isEmpty()) cs.withMounts(mounts);
        List<com.github.dockerjava.api.model.ContainerSpecSecret> secs = buildSecretRefs(d.get("secrets"));
        if (!secs.isEmpty()) cs.withSecrets(secs);
        List<com.github.dockerjava.api.model.ContainerSpecConfig> cfgs = buildConfigRefs(d.get("configs"));
        if (!cfgs.isEmpty()) cs.withConfigs(cfgs);

        // -------- TaskSpec --------
        TaskSpec ts = new TaskSpec().withContainerSpec(cs);
        ResourceRequirements rr = buildResources(d.get("resources"));
        if (rr != null) ts.withResources(rr);
        ServiceRestartPolicy rp = buildRestart(d.get("restart"));
        if (rp != null) ts.withRestartPolicy(rp);
        ServicePlacement pl = buildPlacement(d.get("placement"));
        if (pl != null) ts.withPlacement(pl);

        // -------- ServiceSpec --------
        ServiceSpec spec = new ServiceSpec().withTaskTemplate(ts);
        String name = str(d.get("name"));
        if (name != null && !name.isBlank()) spec.withName(name.trim());
        Map<String, String> labels = strMap(d.get("labels"));
        if (!labels.isEmpty()) spec.withLabels(labels);

        // mode
        String mode = str(d.get("mode"));
        if (mode == null) mode = "replicated";
        ServiceModeConfig sm = new ServiceModeConfig();
        if ("global".equalsIgnoreCase(mode.trim())) {
            sm.withGlobal(new ServiceGlobalModeOptions());
        } else {
            int replicas = intVal(d.get("replicas"), 1);
            if (replicas < 0) throw new BusinessException(40000, "副本数不能为负");
            sm.withReplicated(new ServiceReplicatedModeOptions().withReplicas(replicas));
        }
        spec.withMode(sm);

        List<NetworkAttachmentConfig> networks = buildNetworks(d.get("networks"));
        if (!networks.isEmpty()) spec.withNetworks(networks);

        EndpointSpec es = buildEndpoint(d.get("ports"), str(d.get("endpointMode")));
        if (es != null) spec.withEndpointSpec(es);

        UpdateConfig uc = buildUpdateConfig(d.get("update"));
        if (uc != null) spec.withUpdateConfig(uc);
        UpdateConfig rbc = buildUpdateConfig(d.get("rollback"));
        if (rbc != null) spec.withRollbackConfig(rbc);

        return spec;
    }

    private HealthCheck buildHealth(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        List<String> test = strList(m.get("test"));
        if (test.isEmpty()) return null;   // 未填 test 视为不启用
        HealthCheck hc = new HealthCheck().withTest(test);
        Long iv = secToNs(m.get("intervalSec")); if (iv != null) hc.withInterval(iv);
        Long to = secToNs(m.get("timeoutSec")); if (to != null) hc.withTimeout(to);
        Long sp = secToNs(m.get("startPeriodSec")); if (sp != null) hc.withStartPeriod(sp);
        Integer rt = intVal0(m.get("retries")); if (rt != null) hc.withRetries(rt);
        return hc;
    }

    private List<Mount> buildMounts(Object raw) {
        if (!(raw instanceof List<?> list)) return Collections.emptyList();
        List<Mount> mounts = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String type = str(m.get("type")); if (type == null) type = "volume";
            String source = str(m.get("source"));
            String target = str(m.get("target"));
            if (target == null || target.isBlank()) continue;
            MountType mt = switch (type.toLowerCase(Locale.ROOT)) {
                case "bind" -> MountType.BIND;
                case "tmpfs" -> MountType.TMPFS;
                default -> MountType.VOLUME;
            };
            Mount mount = new Mount().withType(mt).withTarget(target.trim());
            if (source != null && !source.isBlank()) mount.withSource(source.trim());
            if (m.get("readOnly") instanceof Boolean ro) mount.withReadOnly(ro);
            mounts.add(mount);
        }
        return mounts;
    }

    /**
     * secrets payload 条目：[{id, name, target?, uid?, gid?, mode?}]，id 为 Swarm Secret ID（前端从列表选择）。
     * target 空时不指定 File，daemon 默认挂到 /var/run/secrets/<name>。
     */
    private List<com.github.dockerjava.api.model.ContainerSpecSecret> buildSecretRefs(Object raw) {
        if (!(raw instanceof List<?> list)) return Collections.emptyList();
        List<com.github.dockerjava.api.model.ContainerSpecSecret> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String id = str(m.get("id"));
            if (id == null || id.isBlank()) continue;
            var ref = new com.github.dockerjava.api.model.ContainerSpecSecret().withSecretId(id.trim());
            String name = str(m.get("name"));
            if (name != null && !name.isBlank()) ref.withSecretName(name.trim());
            // daemon 要求 secret 引用必须带 File.Name（target 缺省时用资源名，与 CLI 行为一致）
            var file = buildSpecFile(m, name);
            if (file == null) throw new BusinessException(40000, "挂载 secret 时无法确定目标文件名，请指定挂载路径或资源名称");
            ref.withFile(file);
            out.add(ref);
        }
        return out;
    }

    private List<com.github.dockerjava.api.model.ContainerSpecConfig> buildConfigRefs(Object raw) {
        if (!(raw instanceof List<?> list)) return Collections.emptyList();
        List<com.github.dockerjava.api.model.ContainerSpecConfig> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String id = str(m.get("id"));
            if (id == null || id.isBlank()) continue;
            var ref = new com.github.dockerjava.api.model.ContainerSpecConfig().withConfigID(id.trim());
            String name = str(m.get("name"));
            if (name != null && !name.isBlank()) ref.withConfigName(name.trim());
            var file = buildSpecFile(m, name);
            if (file == null) throw new BusinessException(40000, "挂载 config 时无法确定目标文件名，请指定挂载路径或资源名称");
            ref.withFile(file);
            out.add(ref);
        }
        return out;
    }

    private com.github.dockerjava.api.model.ContainerSpecFile buildSpecFile(Map<?, ?> m, String defaultName) {
        String target = str(m.get("target"));
        if (target == null || target.isBlank()) target = defaultName;
        String uid = str(m.get("uid"));
        String gid = str(m.get("gid"));
        Integer mode = intVal0(m.get("mode"));
        // daemon 不接受空字符串 UID/GID（strconv.Atoi 报错），必须像 CLI 一样补默认值 0/0/0444
        if ((target == null || target.isBlank()) && uid == null && gid == null && mode == null) return null;
        var file = new com.github.dockerjava.api.model.ContainerSpecFile();
        if (target != null && !target.isBlank()) file.withName(target.trim());
        file.withUid(uid != null && !uid.isBlank() ? uid.trim() : "0");
        file.withGid(gid != null && !gid.isBlank() ? gid.trim() : "0");
        file.withMode(mode != null ? mode.longValue() : 292L); // 0444
        return file;
    }

    private List<NetworkAttachmentConfig> buildNetworks(Object raw) {
        if (!(raw instanceof List<?> list)) return Collections.emptyList();
        List<NetworkAttachmentConfig> out = new ArrayList<>();
        for (Object o : list) {
            String n = (o instanceof Map<?, ?> m) ? str(m.get("target")) : str(o);
            if (n == null || n.isBlank()) continue;
            NetworkAttachmentConfig na = new NetworkAttachmentConfig().withTarget(n.trim());
            if (o instanceof Map<?, ?> m) {
                List<String> aliases = strList(m.get("aliases"));
                if (!aliases.isEmpty()) na.withAliases(aliases);
            }
            out.add(na);
        }
        return out;
    }

    private EndpointSpec buildEndpoint(Object portsRaw, String mode) {
        List<PortConfig> ports = new ArrayList<>();
        if (portsRaw instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Integer pub = intVal0(m.get("publishedPort"));
                Integer tgt = intVal0(m.get("targetPort"));
                if (tgt == null) continue;
                PortConfig pc = new PortConfig().withTargetPort(tgt);
                if (pub != null) pc.withPublishedPort(pub);
                pc.withProtocol(parseProto(str(m.get("protocol"))));
                PortConfig.PublishMode pm = parsePublishMode(str(m.get("publishMode")));
                if (pm != null) pc.withPublishMode(pm);
                String pName = str(m.get("name"));
                if (pName != null && !pName.isBlank()) pc.withName(pName.trim());
                ports.add(pc);
            }
        }
        EndpointSpec es = new EndpointSpec();
        if (!ports.isEmpty()) es.withPorts(ports);
        EndpointResolutionMode erm = parseEndpointMode(mode);
        if (erm != null) es.withMode(erm);
        return (ports.isEmpty() && erm == null) ? null : es;
    }

    private ResourceRequirements buildResources(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        ResourceSpecs limits = buildResourceSpecs(m.get("limits"));
        ResourceSpecs res = buildResourceSpecs(m.get("reservations"));
        if (limits == null && res == null) return null;
        ResourceRequirements rr = new ResourceRequirements();
        if (limits != null) rr.withLimits(limits);
        if (res != null) rr.withReservations(res);
        return rr;
    }

    private ResourceSpecs buildResourceSpecs(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        ResourceSpecs s = new ResourceSpecs();
        boolean any = false;
        Double cpus = doubleVal0(m.get("cpus"));
        if (cpus != null && cpus > 0) { s.withNanoCPUs(Math.round(cpus * 1e9)); any = true; }
        Integer memMb = intVal0(m.get("memoryMb"));
        if (memMb != null && memMb > 0) { s.withMemoryBytes((long) memMb * 1024L * 1024L); any = true; }
        return any ? s : null;
    }

    private ServiceRestartPolicy buildRestart(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        String cond = str(m.get("condition"));
        if (cond == null || cond.isBlank()) return null;
        ServiceRestartCondition c = switch (cond.toLowerCase(Locale.ROOT)) {
            case "none" -> ServiceRestartCondition.NONE;
            case "on-failure", "on_failure" -> ServiceRestartCondition.ON_FAILURE;
            case "any" -> ServiceRestartCondition.ANY;
            default -> throw new BusinessException(40000, "restart.condition 无效: " + cond);
        };
        ServiceRestartPolicy rp = new ServiceRestartPolicy().withCondition(c);
        Long delay = secToNs(m.get("delaySec")); if (delay != null) rp.withDelay(delay);
        Long wa = secToNs(m.get("windowSec")); if (wa != null) rp.withWindow(wa);
        Integer max = intVal0(m.get("maxAttempts")); if (max != null) rp.withMaxAttempts(max.longValue());
        return rp;
    }

    private ServicePlacement buildPlacement(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        List<String> constraints = strList(m.get("constraints"));
        Integer maxReplicas = intVal0(m.get("maxReplicas"));
        if (constraints.isEmpty() && maxReplicas == null) return null;
        ServicePlacement p = new ServicePlacement();
        if (!constraints.isEmpty()) p.withConstraints(constraints);
        if (maxReplicas != null) p.withMaxReplicas(maxReplicas);
        return p;
    }

    private UpdateConfig buildUpdateConfig(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return null;
        Integer parallelism = intVal0(m.get("parallelism"));
        Long delay = secToNs(m.get("delaySec"));
        String fa = str(m.get("failureAction"));
        String order = str(m.get("order"));
        Integer maxRatio = intVal0(m.get("maxFailureRatio"));
        Long monitor = secToNs(m.get("monitorSec"));
        if (parallelism == null && delay == null && (fa == null || fa.isBlank())
                && (order == null || order.isBlank()) && maxRatio == null && monitor == null) return null;
        UpdateConfig uc = new UpdateConfig();
        if (parallelism != null) uc.withParallelism(parallelism.longValue());
        if (delay != null) uc.withDelay(delay);
        if (monitor != null) uc.withMonitor(monitor);
        if (maxRatio != null) uc.withMaxFailureRatio(maxRatio.floatValue());
        if (fa != null && !fa.isBlank()) {
            UpdateFailureAction a = switch (fa.toLowerCase(Locale.ROOT)) {
                case "continue" -> UpdateFailureAction.CONTINUE;
                case "rollback" -> UpdateFailureAction.ROLLBACK;
                case "pause" -> UpdateFailureAction.PAUSE;
                default -> throw new BusinessException(40000, "failureAction 无效: " + fa);
            };
            uc.withFailureAction(a);
        }
        if (order != null && !order.isBlank()) {
            UpdateOrder o = switch (order.toLowerCase(Locale.ROOT).replace('-', '_')) {
                case "stop_first" -> UpdateOrder.STOP_FIRST;
                case "start_first" -> UpdateOrder.START_FIRST;
                default -> throw new BusinessException(40000, "order 无效: " + order);
            };
            uc.withOrder(o);
        }
        return uc;
    }

    private PortConfigProtocol parseProto(String p) {
        if (p == null) return PortConfigProtocol.TCP;
        return switch (p.toLowerCase(Locale.ROOT)) {
            case "udp" -> PortConfigProtocol.UDP;
            case "sctp" -> PortConfigProtocol.SCTP;
            default -> PortConfigProtocol.TCP;
        };
    }

    private PortConfig.PublishMode parsePublishMode(String m) {
        if (m == null || m.isBlank()) return null;
        return switch (m.toLowerCase(Locale.ROOT)) {
            case "host" -> PortConfig.PublishMode.host;
            case "ingress" -> PortConfig.PublishMode.ingress;
            default -> null;
        };
    }

    private EndpointResolutionMode parseEndpointMode(String m) {
        if (m == null || m.isBlank()) return null;
        return switch (m.toLowerCase(Locale.ROOT)) {
            case "vip" -> EndpointResolutionMode.VIP;
            case "dnsrr" -> EndpointResolutionMode.DNSRR;
            default -> null;
        };
    }

    // ---------- 通用转换 helpers ----------

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static List<String> strList(Object o) {
        if (o == null) return Collections.emptyList();
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object x : list) if (x != null) out.add(String.valueOf(x));
            return out;
        }
        if (o instanceof String s && !s.isBlank()) return List.of(s.trim().split("\\s+"));
        return Collections.emptyList();
    }

    /** env 支持两种写法：["K=V","K2=V2"] 或 {K:"V",K2:"V2"} */
    private static List<String> envList(Object o) {
        if (o instanceof Map<?, ?> m) {
            List<String> out = new ArrayList<>();
            m.forEach((k, v) -> out.add(String.valueOf(k) + "=" + (v == null ? "" : String.valueOf(v))));
            return out;
        }
        return strList(o);
    }

    private static Map<String, String> strMap(Object o) {
        if (!(o instanceof Map<?, ?> m)) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k != null && v != null) out.put(String.valueOf(k), String.valueOf(v));
        });
        return out;
    }

    private static int intVal(Object o, int def) {
        Integer v = intVal0(o);
        return v == null ? def : v;
    }

    private static Integer intVal0(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static Double doubleVal0(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    /** 秒 → 纳秒（docker spec 时长字段单位）*/
    private static Long secToNs(Object o) {
        Double d = doubleVal0(o);
        if (d == null || d <= 0) return null;
        return Math.round(d * 1_000_000_000L);
    }

    // ---------- 其他服务操作（保持不变，稍后统一支持编辑） ----------

    public void updateService(Long envId, String serviceId, Map<String, Object> updates) {
        DockerClient client = dockerClientManager.getClient(envId);
        var existing = client.inspectServiceCmd(serviceId).exec();
        // 复用创建时的 builder 重新拼装 spec，保证前端看到什么就下发什么（避免不同字段零碎合并遗漏）
        ServiceSpec newSpec = buildServiceSpec(updates);
        if (newSpec.getName() == null || newSpec.getName().isBlank()) {
            newSpec.withName(existing.getSpec().getName());
        }
        long version = existing.getVersion().getIndex();
        client.updateServiceCmd(serviceId, newSpec).withVersion(version).exec();
        log.info("Service updated: {}", serviceId);
    }

    public void scaleService(Long envId, String serviceId, int replicas) {
        DockerClient client = dockerClientManager.getClient(envId);
        var service = client.inspectServiceCmd(serviceId).exec();
        var serviceSpec = service.getSpec();
        serviceSpec.getMode().getReplicated().withReplicas(replicas);
        long version = service.getVersion().getIndex();
        client.updateServiceCmd(serviceId, serviceSpec).withVersion(version).exec();
        log.info("Service scaled: {} -> {} replicas", serviceId, replicas);
    }

    public void removeService(Long envId, String serviceId) {
        DockerClient client = dockerClientManager.getClient(envId);
        client.removeServiceCmd(serviceId).exec();
        log.info("Service removed: {}", serviceId);
    }

    public List<Task> listTasks(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        return client.listTasksCmd().exec();
    }

    public List<Task> listServiceTasks(Long envId, String serviceId) {
        DockerClient client = dockerClientManager.getClient(envId);
        List<Task> allTasks = client.listTasksCmd().exec();
        return allTasks.stream().filter(t -> serviceId.equals(t.getServiceId())).toList();
    }

    public String getServiceLogs(Long envId, String serviceId, int tail) {
        DockerClient client = dockerClientManager.getClient(envId);
        return streamSwarmLogs(client.logServiceCmd(serviceId), tail);
    }

    /**
     * 单个副本（task）的日志：/tasks/{id}/logs，daemon 会定位到任务所在节点取日志，
     * 避免在多节点集群上用 manager 本地容器日志接口报 404
     */
    public String getTaskLogs(Long envId, String taskId, int tail) {
        DockerClient client = dockerClientManager.getClient(envId);
        return streamSwarmLogs(client.logTaskCmd(taskId), tail);
    }

    /**
     * 探测“进入该副本终端”应该怎么走：
     * <ul>
     *   <li>local=true：容器就在当前连接的 daemon 上，直接用 docker exec（无需 SSH 凭据）</li>
     *   <li>local=false：副本跑在其他节点，只能 SSH 到 nodeAddress 后执行 docker exec</li>
     * </ul>
     */
    public Map<String, Object> resolveTaskExecTarget(Long envId, String taskId) {
        DockerClient client = dockerClientManager.getClient(envId);
        Task task = client.listTasksCmd().withIdFilter(taskId).exec().stream().findFirst()
                .orElseThrow(() -> new BusinessException(40400, "任务不存在或已被清理"));
        String containerId = task.getStatus() == null || task.getStatus().getContainerStatus() == null
                ? null : task.getStatus().getContainerStatus().getContainerID();
        String state = task.getStatus() == null || task.getStatus().getState() == null
                ? null : task.getStatus().getState().name().toLowerCase(Locale.ROOT);

        boolean local = false;
        if (containerId != null && !containerId.isBlank()) {
            try {
                client.inspectContainerCmd(containerId).exec();
                local = true;
            } catch (com.github.dockerjava.api.exception.NotFoundException e) {
                // 典型情况：副本在 worker 节点，manager 本地查不到，属预期分支
                local = false;
            } catch (Exception e) {
                log.debug("探测容器 {} 是否本地可见失败：{}", containerId, e.getMessage());
                local = false;
            }
        }

        String nodeId = task.getNodeId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.getId());
        result.put("nodeId", nodeId);
        result.put("containerId", containerId);
        result.put("state", state);
        result.put("local", local);
        result.put("sshConfigured", nodeId != null && sshService.getConfigByNode(envId, nodeId) != null);
        result.put("nodeAddress", resolveNodeAddress(client, nodeId));
        return result;
    }

    /** 节点 IP 在 Status.Address（不在 Description），用于提示去哪台机器配 SSH */
    private String resolveNodeAddress(DockerClient client, String nodeId) {
        if (nodeId == null) return null;
        try {
            return client.listSwarmNodesCmd().exec().stream()
                    .filter(n -> nodeId.equals(n.getId()))
                    .map(n -> n.getStatus() == null ? null : n.getStatus().getAddress())
                    .findFirst().orElse(null);
        } catch (Exception e) {
            log.debug("读取节点 {} 地址失败：{}", nodeId, e.getMessage());
            return null;
        }
    }

    private String streamSwarmLogs(com.github.dockerjava.api.command.LogSwarmObjectCmd cmd, int tail) {
        StringBuilder logs = new StringBuilder();
        try {
            cmd.withStdout(true)
                    .withStderr(true)
                    .withTail(tail)
                    .withTimestamps(true)
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
