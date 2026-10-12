package com.dockerportal.service;

import com.dockerportal.docker.DockerClientManager;
import com.dockerportal.exception.BusinessException;
import com.dockerportal.model.entity.StackCompose;
import com.dockerportal.repository.StackComposeRepository;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Secret;
import com.github.dockerjava.api.model.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Stack 管理服务
 * Stack 是 docker-compose 文件在 Swarm 中的部署单元。
 * deployStack 解析 compose YAML（常用指令子集）并翻译成 Swarm 服务/网络：
 * 支持 services[].image/ports/environment/volumes/networks/command/entrypoint/labels/
 * workdir/user/hostname/tty/read_only/stop_grace_period/stop_signal/healthcheck 与
 * deploy.replicas/mode/labels/placement/restart_policy/update_config/rollback_config/resources/secrets/configs；
 * 顶层 networks（driver/internal/attachable）与 secrets/configs（external 或已存在引用）。
 * 不支持：build、相对路径 bind 挂载、env_file、secrets 的 file 内联、变量插值 ${VAR}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StackService {

    private static final String STACK_LABEL = "com.docker.stack.namespace";
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9_-]*$");
    /** compose 原文入库字段上限，同时挡住误粘贴进来的超大文件 */
    private static final int MAX_COMPOSE_CHARS = 100_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DockerClientManager dockerClientManager;
    private final SwarmService swarmService;
    private final StackComposeRepository stackComposeRepository;

    /**
     * 列出所有 Stack（通过服务标签 com.docker.stack.namespace 分组）
     */
    public List<Map<String, Object>> listStacks(Long envId) {
        DockerClient client = dockerClientManager.getClient(envId);
        List<com.github.dockerjava.api.model.Service> services = client.listServicesCmd().exec();

        Map<String, List<com.github.dockerjava.api.model.Service>> stackMap = services.stream()
                .filter(s -> s.getSpec() != null && s.getSpec().getLabels() != null)
                .filter(s -> s.getSpec().getLabels().containsKey(STACK_LABEL))
                .collect(Collectors.groupingBy(
                        s -> s.getSpec().getLabels().get(STACK_LABEL)
                ));

        // 一次查出该环境已记录的 compose，避免逐行查库
        Map<String, StackCompose> stored = stackComposeRepository.findByEnvironmentId(envId).stream()
                .collect(Collectors.toMap(StackCompose::getStackName, c -> c, (a, b) -> a));

        return stackMap.entrySet().stream().map(entry -> {
            Map<String, Object> stack = new LinkedHashMap<>();
            stack.put("name", entry.getKey());
            stack.put("serviceCount", entry.getValue().size());
            stack.put("services", entry.getValue().stream()
                    .map(s -> s.getSpec().getName())
                    .toList());
            // 是否留有 compose 原文：决定列表页的「编辑」能否回填内容
            stack.put("hasCompose", stored.containsKey(entry.getKey()));
            return stack;
        }).collect(Collectors.toList());
    }

    /**
     * 取回部署时记录的 compose 原文。
     * Docker 侧不保存 compose 文件，非本平台部署的 Stack 没有记录，
     * 此时 stored=false，由前端提示用户自行粘贴或上传完整 compose。
     */
    public Map<String, Object> getCompose(Long envId, String stackName) {
        StackCompose record = stackComposeRepository.findByEnvironmentIdAndStackName(envId, stackName).orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stored", record != null);
        if (record != null) {
            result.put("content", record.getContent());
            result.put("updatedAt", record.getUpdatedAt() == null ? null : TIME_FORMAT.format(record.getUpdatedAt()));
        }
        return result;
    }

    /**
     * 获取 Stack 详情（包含的服务列表）
     */
    public Map<String, Object> getStack(Long envId, String stackName) {
        DockerClient client = dockerClientManager.getClient(envId);
        List<com.github.dockerjava.api.model.Service> services = client.listServicesCmd().exec();

        List<com.github.dockerjava.api.model.Service> stackServices = services.stream()
                .filter(s -> s.getSpec() != null && s.getSpec().getLabels() != null)
                .filter(s -> stackName.equals(s.getSpec().getLabels().get(STACK_LABEL)))
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", stackName);
        result.put("services", stackServices.stream().map(s -> {
            Map<String, Object> svc = new LinkedHashMap<>();
            svc.put("id", s.getId());
            svc.put("name", s.getSpec().getName());
            svc.put("image", s.getSpec().getTaskTemplate().getContainerSpec().getImage());
            return svc;
        }).toList());

        return result;
    }

    /**
     * 部署 Stack：解析 compose YAML，网络先建、服务逐个 create/update（幂等，可重复部署实现更新）
     */
    public Map<String, Object> deployStack(Long envId, String stackName, String composeContent) {
        if (stackName == null || !NAME_PATTERN.matcher(stackName).matches()) {
            throw new BusinessException(40000, "Stack 名称只允许小写字母、数字、下划线和连字符，且以字母或数字开头");
        }
        if (composeContent == null || composeContent.isBlank()) {
            throw new BusinessException(40000, "compose 内容不能为空");
        }
        if (composeContent.length() > MAX_COMPOSE_CHARS) {
            throw new BusinessException(40000, "compose 内容过大（上限 " + MAX_COMPOSE_CHARS + " 字符）");
        }
        Map<String, Object> root;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setMaxAliasesForCollections(100);
            Yaml yaml = new Yaml(new SafeConstructor(options));
            root = yaml.load(composeContent);
        } catch (Exception e) {
            throw new BusinessException(40000, "compose YAML 解析失败: " + e.getMessage());
        }
        if (root == null || !(root.get("services") instanceof Map<?, ?> servicesRaw) || servicesRaw.isEmpty()) {
            throw new BusinessException(40000, "compose 文件缺少 services 定义");
        }
        // version 字段已废弃，忽略
        root.remove("version");

        DockerClient client = dockerClientManager.getClient(envId);
        List<String> warnings = new ArrayList<>();

        // ---- 顶层 networks：确保存在 ----
        Set<String> stackNetworks = new LinkedHashSet<>();
        Map<String, Object> networksRaw = root.get("networks") instanceof Map<?, ?> m
                ? castMap(m) : Collections.emptyMap();
        for (String key : networksRaw.keySet()) {
            String netName = stackName + "_" + key;
            ensureNetwork(client, netName, castMap(networksRaw.get(key)), stackName);
            stackNetworks.add(netName);
        }
        // 隐式 default 网络（有服务未指定 networks 时才会真正用到，统一预建保证可加入）
        String defaultNet = stackName + "_default";
        ensureNetwork(client, defaultNet, Map.of(), stackName);
        stackNetworks.add(defaultNet);

        // ---- 顶层 secrets/configs 名称→ID 解析表 ----
        Map<String, String> secretIds = resolveResourceIds(client, root.get("secrets"), "secret", stackName, warnings);
        Map<String, String> configIds = resolveResourceIds(client, root.get("configs"), "config", stackName, warnings);

        // ---- services ----
        List<com.github.dockerjava.api.model.Service> existing = client.listServicesCmd().exec();
        Map<String, com.github.dockerjava.api.model.Service> byName = existing.stream()
                .filter(s -> s.getSpec() != null && s.getSpec().getName() != null)
                .collect(Collectors.toMap(s -> s.getSpec().getName(), s -> s, (a, b) -> a));

        List<String> created = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        for (Map.Entry<?, ?> entry : servicesRaw.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Map<String, Object> svc = castMap(entry.getValue());
            if (svc == null) continue;
            Map<String, Object> payload = toServicePayload(stackName, key, svc, defaultNet,
                    secretIds, configIds, stackNetworks, warnings);

            String fullName = stackName + "_" + key;
            var exist = byName.get(fullName);
            if (exist != null) {
                swarmService.updateService(envId, exist.getId(), payload);
                updated.add(fullName);
            } else {
                swarmService.createService(envId, payload);
                created.add(fullName);
            }
        }

        // ---- 清理 compose 里已删除的服务 ----
        Set<String> declared = servicesRaw.keySet().stream()
                .map(k -> stackName + "_" + k).collect(Collectors.toSet());
        List<String> removed = new ArrayList<>();
        for (var s : existing) {
            if (s.getSpec() != null && s.getSpec().getLabels() != null
                    && stackName.equals(s.getSpec().getLabels().get(STACK_LABEL))
                    && !declared.contains(s.getSpec().getName())) {
                client.removeServiceCmd(s.getId()).exec();
                removed.add(s.getSpec().getName());
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("created", created);
        summary.put("updated", updated);
        summary.put("removed", removed);
        summary.put("warnings", warnings);
        // 部署全部成功后才落库：中途失败时保留上一次的可用 compose，不被半截内容覆盖
        recordCompose(envId, stackName, composeContent);
        log.info("Stack deployed: {} created={} updated={} removed={}", stackName, created, updated, removed);
        return summary;
    }

    /** 保存/覆盖 compose 原文，供列表页「编辑」回填 */
    private void recordCompose(Long envId, String stackName, String content) {
        StackCompose record = stackComposeRepository.findByEnvironmentIdAndStackName(envId, stackName).orElse(null);
        if (record == null) {
            record = StackCompose.builder().environmentId(envId).stackName(stackName).content(content).build();
        } else {
            record.setContent(content);
        }
        stackComposeRepository.save(record);
    }

    /**
     * 删除 Stack（删除该 stack 下的所有服务与网络）
     */
    public void removeStack(Long envId, String stackName) {
        DockerClient client = dockerClientManager.getClient(envId);
        List<com.github.dockerjava.api.model.Service> services = client.listServicesCmd().exec();

        List<com.github.dockerjava.api.model.Service> stackServices = services.stream()
                .filter(s -> s.getSpec() != null && s.getSpec().getLabels() != null)
                .filter(s -> stackName.equals(s.getSpec().getLabels().get(STACK_LABEL)))
                .toList();
        for (var service : stackServices) {
            client.removeServiceCmd(service.getId()).exec();
            log.info("Stack service removed: {}", service.getSpec().getName());
        }
        // Swarm 释放服务 task 是异步的：不等待直接删网络会因 endpoint 仍被占用而失败，
        // 导致网络残留（对齐 docker CLI "stack rm" 的先收敛再清理行为）
        if (!stackServices.isEmpty()) {
            waitForServicesDrain(client, stackServices.stream().map(com.github.dockerjava.api.model.Service::getId).toList());
        }

        // 删除带同命名空间标签的网络（失败重试几次，等待网络引用释放）
        int nets = 0;
        for (Network net : client.listNetworksCmd().exec()) {
            if (net.getLabels() != null && stackName.equals(net.getLabels().get(STACK_LABEL))) {
                for (int attempt = 0; ; attempt++) {
                    try {
                        client.removeNetworkCmd(net.getId()).exec();
                        nets++;
                        break;
                    } catch (Exception e) {
                        if (attempt >= 4) {
                            log.warn("Remove stack network {} failed after retries: {}", net.getName(), e.getMessage());
                            break;
                        }
                        sleepQuietly(1000);
                    }
                }
            }
        }
        log.info("Stack removed: {} ({} services, {} networks)", stackName, stackServices.size(), nets);
        // 同时清除 compose 记录：留着它会让下次用同名部署新 Stack 时回填出完全不相干的内容
        stackComposeRepository.findByEnvironmentIdAndStackName(envId, stackName)
                .ifPresent(stackComposeRepository::delete);
    }

    /** 轮询等待服务 task 收敛（desired state 全部进入 REMOVE），最长约 30s */
    private void waitForServicesDrain(DockerClient client, List<String> serviceIds) {
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline) {
            boolean alive = client.listTasksCmd().exec().stream()
                    .anyMatch(t -> t.getServiceId() != null && serviceIds.contains(t.getServiceId())
                            && t.getDesiredState() != com.github.dockerjava.api.model.TaskState.REMOVE);
            if (!alive) return;
            sleepQuietly(500);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== compose → payload 翻译 ====================

    private Map<String, Object> toServicePayload(String stack, String key, Map<String, Object> svc,
                                                 String defaultNet, Map<String, String> secretIds,
                                                 Map<String, String> configIds, Set<String> stackNetworks,
                                                 List<String> warnings) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("name", stack + "_" + key);
        String image = str(svc.get("image"));
        if (image == null || image.isBlank()) {
            throw new BusinessException(40000, "服务 " + key + " 缺少 image（stack 部署不支持 build）");
        }
        p.put("image", image.trim());

        // labels：service.labels + deploy.labels + stack 命名空间
        Map<String, String> labels = new LinkedHashMap<>(strMap(svc.get("labels")));
        Map<String, Object> deploy = castMap(svc.get("deploy"));
        if (deploy != null) labels.putAll(strMap(deploy.get("labels")));
        labels.put(STACK_LABEL, stack);
        p.put("labels", labels);

        List<String> ep = splitCmd(svc.get("entrypoint"));
        if (!ep.isEmpty()) p.put("entrypoint", ep);
        List<String> cmd = splitCmd(svc.get("command"));
        if (!cmd.isEmpty()) p.put("command", cmd);
        if (svc.get("environment") != null) p.put("env", svc.get("environment"));
        putIfText(p, "workDir", str(svc.get("workdir")));
        putIfText(p, "user", str(svc.get("user")));
        putIfText(p, "hostname", str(svc.get("hostname")));
        if (svc.get("tty") instanceof Boolean b && b) p.put("tty", true);
        if (svc.get("read_only") instanceof Boolean b) p.put("readOnly", b);
        Double stopGrace = durationSec(svc.get("stop_grace_period"));
        if (stopGrace != null) p.put("stopGracePeriod", stopGrace);
        putIfText(p, "stopSignal", str(svc.get("stop_signal")));

        Object hcRaw = svc.get("healthcheck");
        if (hcRaw instanceof Map<?, ?> hcMap) {
            Map<String, Object> hc = new LinkedHashMap<>();
            Object test = hcMap.get("test");
            if (test instanceof List<?> l && !l.isEmpty() && !"NONE".equalsIgnoreCase(String.valueOf(l.get(0)))) {
                hc.put("test", l);
            } else if (test instanceof String s && !s.isBlank() && !"NONE".equalsIgnoreCase(s)) {
                hc.put("test", List.of("CMD-SHELL", s));
            }
            Double iv = durationSec(hcMap.get("interval")); if (iv != null) hc.put("intervalSec", iv);
            Double to = durationSec(hcMap.get("timeout")); if (to != null) hc.put("timeoutSec", to);
            Double sp = durationSec(hcMap.get("start_period")); if (sp != null) hc.put("startPeriodSec", sp);
            if (hcMap.get("retries") != null) hc.put("retries", hcMap.get("retries"));
            if (Boolean.TRUE.equals(hcMap.get("disable"))) hc.put("test", Collections.emptyList());
            if (!hc.isEmpty()) p.put("healthcheck", hc);
        }

        // volumes
        List<Map<String, Object>> mounts = expandVolumes(svc.get("volumes"), stack, warnings);
        if (!mounts.isEmpty()) p.put("mounts", mounts);

        // ports
        List<Map<String, Object>> ports = expandPorts(svc.get("ports"));
        if (!ports.isEmpty()) p.put("ports", ports);

        // networks：显式声明→stack 前缀；未声明→default 网络
        List<String> netNames = new ArrayList<>();
        Object netsRaw = svc.get("networks");
        if (netsRaw instanceof List<?> list) {
            for (Object o : list) {
                String n = (o instanceof Map<?, ?> m) ? str(m.get("target")) : str(o);
                if (n != null && !n.isBlank()) netNames.add(stack + "_" + n.trim());
            }
        } else if (netsRaw instanceof Map<?, ?> m) {
            for (Object k : m.keySet()) netNames.add(stack + "_" + k);
        }
        if (netNames.isEmpty()) netNames.add(defaultNet);
        p.put("networks", netNames);

        // deploy 块
        if (deploy != null) {
            String mode = str(deploy.get("mode"));
            if (mode != null) p.put("mode", mode);
            if (deploy.get("replicas") != null) p.put("replicas", deploy.get("replicas"));
            putIfText(p, "endpointMode", str(deploy.get("endpoint_mode")));
            if (deploy.get("restart_policy") instanceof Map<?, ?> rp) {
                Map<String, Object> restart = new LinkedHashMap<>();
                putIfText(restart, "condition", str(rp.get("condition")));
                Double d = durationSec(rp.get("delay")); if (d != null) restart.put("delaySec", d);
                Double w = durationSec(rp.get("window")); if (w != null) restart.put("windowSec", w);
                if (rp.get("max_attempts") != null) restart.put("maxAttempts", rp.get("max_attempts"));
                if (!restart.isEmpty()) p.put("restart", restart);
            }
            if (deploy.get("placement") instanceof Map<?, ?> pl) {
                Map<String, Object> placement = new LinkedHashMap<>();
                Object constraints = pl.get("constraints");
                if (constraints != null) placement.put("constraints",
                        constraints instanceof List<?> ? constraints : List.of(String.valueOf(constraints)));
                if (!placement.isEmpty()) p.put("placement", placement);
            }
            if (deploy.get("update_config") instanceof Map<?, ?> uc) {
                Map<String, Object> update = mapUpdateConfig(uc);
                if (!update.isEmpty()) p.put("update", update);
            }
            if (deploy.get("rollback_config") instanceof Map<?, ?> rc) {
                Map<String, Object> rollback = mapUpdateConfig(rc);
                if (!rollback.isEmpty()) p.put("rollback", rollback);
            }
            if (deploy.get("resources") instanceof Map<?, ?> res) {
                Map<String, Object> resources = new LinkedHashMap<>();
                if (res.get("limits") instanceof Map<?, ?> lm) {
                    Map<String, Object> limits = mapResourceSpec(lm);
                    if (!limits.isEmpty()) resources.put("limits", limits);
                }
                if (res.get("reservations") instanceof Map<?, ?> rm) {
                    Map<String, Object> reservations = mapResourceSpec(rm);
                    if (!reservations.isEmpty()) resources.put("reservations", reservations);
                }
                if (!resources.isEmpty()) p.put("resources", resources);
            }
            // deploy.secrets / deploy.configs
            List<Map<String, Object>> secRefs = expandResourceRefs(deploy.get("secrets"), secretIds, "secret");
            if (!secRefs.isEmpty()) p.put("secrets", secRefs);
            List<Map<String, Object>> cfgRefs = expandResourceRefs(deploy.get("configs"), configIds, "config");
            if (!cfgRefs.isEmpty()) p.put("configs", cfgRefs);
        }
        return p;
    }

    private Map<String, Object> mapUpdateConfig(Map<?, ?> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> src = castMap(raw);
        if (src == null) return m;
        if (src.get("parallelism") != null) m.put("parallelism", src.get("parallelism"));
        Double d = durationSec(src.get("delay")); if (d != null) m.put("delaySec", d);
        Double mo = durationSec(src.get("monitor")); if (mo != null) m.put("monitorSec", mo);
        putIfText(m, "failureAction", str(src.get("failure_action")));
        putIfText(m, "order", str(src.get("order")));
        return m;
    }

    private Map<String, Object> mapResourceSpec(Map<?, ?> raw) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> src = castMap(raw);
        if (src == null) return m;
        if (src.get("cpus") != null) {
            Object c = src.get("cpus");
            m.put("cpus", c instanceof Map<?, ?> cm ? cm.get("nano_cpus") : c);
        }
        Integer mb = memoryMb(src.get("memory"));
        if (mb != null) m.put("memoryMb", mb);
        return m;
    }

    /** deploy.secrets: ["mysec"] 或 [{source,target,uid,gid,mode}] → payload 引用 */
    private List<Map<String, Object>> expandResourceRefs(Object raw, Map<String, String> idMap, String kind) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null) return out;
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            String source;
            Map<String, Object> ref = new LinkedHashMap<>();
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> mm = castMap(m);
                source = str(mm.get("source"));
                putIfText(ref, "target", str(mm.get("target")));
                putIfText(ref, "uid", str(mm.get("uid")));
                putIfText(ref, "gid", str(mm.get("gid")));
                if (mm.get("mode") != null) ref.put("mode", mm.get("mode"));
            } else {
                source = str(o);
            }
            if (source == null || source.isBlank()) continue;
            String id = idMap.get(source.trim());
            if (id == null) {
                throw new BusinessException(40000, "引用了不存在的 " + kind + " \"" + source
                        + "\"，请先在对应页面创建，或在 compose 顶层 " + kind + "s 中声明 external");
            }
            ref.put("id", id);
            ref.put("name", source.trim());
            out.add(ref);
        }
        return out;
    }

    /** 顶层 secrets/configs → 名称:ID。external:true 按名查找；file 内联不支持 */
    private Map<String, String> resolveResourceIds(DockerClient client, Object raw, String kind,
                                                   String stack, List<String> warnings) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!(raw instanceof Map<?, ?> m)) return out;
        Map<String, Object> defs = castMap(m);
        // 现网资源列表（名称可能带 stack 前缀，两种都匹配）
        List<Map<String, String>> available = listSwarmResources(client, kind);
        for (Map.Entry<String, Object> e : defs.entrySet()) {
            String key = e.getKey();
            Map<String, Object> def = e.getValue() instanceof Map<?, ?> dm ? castMap(dm) : Collections.emptyMap();
            String lookupName = str(def.get("name")) != null ? str(def.get("name")) : key;
            String id = null;
            for (Map<String, String> a : available) {
                if (a.get("name").equals(lookupName) || a.get("name").equals(stack + "_" + lookupName)) {
                    id = a.get("id");
                    break;
                }
            }
            if (id == null && Boolean.TRUE.equals(def.get("external"))) {
                throw new BusinessException(40000, "external " + kind + " \"" + lookupName + "\" 在当前集群不存在，请先创建");
            }
            if (id == null && def.get("file") != null) {
                throw new BusinessException(40000, "Stack 部署暂不支持 file 内联 " + kind + "（\"" + lookupName
                        + "\"），请先在 " + kind + " 页面创建同名资源");
            }
            if (id != null) out.put(key, id);
        }
        return out;
    }

    /** 统一列出集群 secret/config：[{id,name}]，kind=secret|config */
    static List<Map<String, String>> listSwarmResources(DockerClient client, String kind) {
        List<Map<String, String>> out = new ArrayList<>();
        if ("config".equals(kind)) {
            for (Config c : client.listConfigsCmd().exec()) {
                out.add(Map.of("id", c.getId(), "name", c.getSpec() == null || c.getSpec().getName() == null
                        ? "" : c.getSpec().getName()));
            }
        } else {
            for (Secret s : client.listSecretsCmd().exec()) {
                out.add(Map.of("id", s.getId(), "name", s.getSpec() == null || s.getSpec().getName() == null
                        ? "" : s.getSpec().getName()));
            }
        }
        return out;
    }

    private List<Map<String, Object>> expandPorts(Object raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) return out;
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {  // 长语法
                Map<String, Object> mm = castMap(m);
                Integer target = portNum(mm.get("target") != null ? mm.get("target") : mm.get("published"));
                Integer published = portNum(mm.get("published"));
                if (target == null) continue;
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("targetPort", target);
                if (published != null) p.put("publishedPort", published);
                putIfText(p, "protocol", str(mm.get("protocol")));
                putIfText(p, "publishMode", str(mm.get("mode")));
                out.add(p);
                continue;
            }
            // 短语法 [ip:]host[-end]:container[/proto]，或 container[/proto]
            String s = String.valueOf(o).trim();
            String proto = "tcp";
            if (s.contains("/")) {
                int slash = s.lastIndexOf('/');
                proto = s.substring(slash + 1);
                s = s.substring(0, slash);
            }
            String[] parts = s.split(":");
            try {
                if (parts.length == 1) {  // 仅容器端口
                    int target = Integer.parseInt(parts[0].trim());
                    out.add(portEntry(target, null, proto));
                } else {
                    String hostPart = parts.length == 3 ? parts[1] : parts[0];
                    String ctrPart = parts.length == 3 ? parts[2] : parts[1];
                    if (ctrPart.isBlank()) ctrPart = hostPart;  // "80:80"? 不会空；防御
                    int target = Integer.parseInt(ctrPart.trim().split("-")[0]);
                    if (hostPart.contains("-")) {  // 端口段：逐对展开（取段宽，起始对齐）
                        String[] hb = hostPart.split("-");
                        int hStart = Integer.parseInt(hb[0]);
                        int cStart = target;
                        for (int i = 0; i <= Integer.parseInt(hb[1]) - hStart; i++) {
                            out.add(portEntry(cStart + i, hStart + i, proto));
                        }
                    } else if (hostPart.isBlank()) {
                        out.add(portEntry(target, null, proto));
                    } else {
                        out.add(portEntry(target, Integer.parseInt(hostPart.trim()), proto));
                    }
                }
            } catch (NumberFormatException e) {
                throw new BusinessException(40000, "端口映射格式无法解析: " + o);
            }
        }
        return out;
    }

    private Map<String, Object> portEntry(int target, Integer published, String proto) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("targetPort", target);
        if (published != null) p.put("publishedPort", published);
        p.put("protocol", proto);
        return p;
    }

    private Integer portNum(Object o) {
        if (o == null) return null;
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (NumberFormatException e) { return null; }
    }

    private List<Map<String, Object>> expandVolumes(Object raw, String stack, List<String> warnings) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (raw == null) return out;
        if (!(raw instanceof List<?> list)) {
            warnings.add("volumes 使用了不支持的写法，已忽略");
            return out;
        }
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {  // 长语法 {type, source, target, read_only}
                Map<String, Object> mm = castMap(m);
                Map<String, Object> mount = new LinkedHashMap<>();
                putIfText(mount, "type", str(mm.get("type")));
                putIfText(mount, "source", str(mm.get("source")));
                putIfText(mount, "target", str(mm.get("target")));
                if (mm.get("read_only") instanceof Boolean ro) mount.put("readOnly", ro);
                if (mount.get("target") != null) out.add(mount);
                continue;
            }
            String s = String.valueOf(o).trim();
            String[] parts = s.split(":");
            if (parts.length < 2 || parts.length > 3) {
                warnings.add("挂载写法无法解析，已忽略: " + s);
                continue;
            }
            String source = parts[0].trim();
            String target = parts[1].trim();
            boolean readOnly = parts.length == 3 && parts[2].contains("ro");
            Map<String, Object> mount = new LinkedHashMap<>();
            if (source.startsWith("/") || source.startsWith("~")) {
                mount.put("type", "bind");
                mount.put("source", source);
            } else if (source.startsWith(".")) {
                throw new BusinessException(40000, "Stack 部署不支持相对路径 bind 挂载（" + s
                        + "）：网页粘贴无法确定宿主机目录，请改用绝对路径或命名卷");
            } else {
                mount.put("type", "volume");
                mount.put("source", stack + "_" + source);  // 命名卷按 docker stack 规则加 stack 前缀
            }
            mount.put("target", target);
            mount.put("readOnly", readOnly);
            out.add(mount);
        }
        return out;
    }

    private void ensureNetwork(DockerClient client, String name, Map<String, Object> spec, String stack) {
        boolean exists = client.listNetworksCmd().withNameFilter(name).exec().stream()
                .anyMatch(n -> name.equals(n.getName()));
        if (exists) return;
        Map<String, Object> ns = spec == null ? Collections.emptyMap() : spec;
        String driver = str(ns.get("driver"));
        var cmd = client.createNetworkCmd()
                .withName(name)
                .withDriver(driver != null && !driver.isBlank() ? driver : "overlay")
                .withAttachable(true)
                .withLabels(Map.of(STACK_LABEL, stack));
        if (Boolean.TRUE.equals(ns.get("internal"))) cmd.withInternal(true);
        cmd.exec();
    }

    /** command/entrypoint：字符串按空白拆分（保留引号内内容），列表原样 */
    private List<String> splitCmd(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object x : list) if (x != null) out.add(String.valueOf(x));
            return out;
        }
        if (raw instanceof String s && !s.isBlank()) {
            List<String> out = new ArrayList<>();
            Matcher m = Pattern.compile("'[^']*'|\\\"[^\\\"]*\\\"|[^\\s]+").matcher(s.trim());
            while (m.find()) {
                String tok = m.group();
                if (tok.startsWith("'") || tok.startsWith("\"")) tok = tok.substring(1, tok.length() - 1);
                out.add(tok);
            }
            return out;
        }
        return Collections.emptyList();
    }

    /** compose 时长 "30s"/"1m"/"500ms"/纯数字(秒)/{seconds:..} → Double 秒 */
    private Double durationSec(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return n.doubleValue();
        if (raw instanceof Map<?, ?> m) {
            Object sec = castMap(m).get("seconds");
            return sec instanceof Number n2 ? n2.doubleValue() : null;
        }
        String s = String.valueOf(raw).trim().toLowerCase();
        if (s.isEmpty()) return null;
        try {
            if (s.endsWith("ms")) return Double.parseDouble(s.substring(0, s.length() - 2)) / 1000.0;
            if (s.endsWith("h")) return Double.parseDouble(s.substring(0, s.length() - 1)) * 3600.0;
            if (s.endsWith("m")) return Double.parseDouble(s.substring(0, s.length() - 1)) * 60.0;
            if (s.endsWith("s")) return Double.parseDouble(s.substring(0, s.length() - 1));
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            warningsThrow(raw);
            return null;
        }
    }

    private void warningsThrow(Object raw) {
        throw new BusinessException(40000, "无法解析时长: " + raw);
    }

    /** compose 内存 "1g"/"512m"/"1024k"/字节数 → Integer MB */
    private Integer memoryMb(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Number n) return (int) (n.longValue() / 1024 / 1024);
        String s = String.valueOf(raw).trim().toLowerCase();
        if (s.isEmpty()) return null;
        try {
            double num = Double.parseDouble(s.replaceAll("[a-z]+$", ""));
            if (s.endsWith("gb") || s.endsWith("g")) return (int) Math.round(num * 1024);
            if (s.endsWith("mb") || s.endsWith("m")) return (int) Math.round(num);
            if (s.endsWith("kb") || s.endsWith("k")) return (int) Math.round(num / 1024);
            if (s.endsWith("b")) return (int) (Long.parseLong(s.replaceAll("[a-z]+$", "")) / 1024 / 1024);
            return (int) (Long.parseLong(s) / 1024 / 1024);
        } catch (NumberFormatException e) {
            throw new BusinessException(40000, "无法解析内存值: " + raw);
        }
    }

    // ==================== 通用 helpers ====================

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static void putIfText(Map<String, Object> m, String key, String v) {
        if (v != null && !v.isBlank()) m.put(key, v.trim());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o == null) return null;
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return null;
    }

    private static Map<String, String> strMap(Object o) {
        if (!(o instanceof Map<?, ?> m)) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        m.forEach((k, v) -> {
            if (k != null && v != null) out.put(String.valueOf(k), String.valueOf(v));
        });
        return out;
    }
}
