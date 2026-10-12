package com.dockerportal.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 容器创建请求，覆盖 docker run 的全部可配置项。
 * 字段值统一使用 docker CLI / compose 的语义（如 --memory、--cpus、-p、--mount、--health-cmd 等），
 * 由 ContainerService 负责翻译为 docker-java 的 CreateContainerCmd / HostConfig。
 */
@Data
public class ContainerCreateRequest {

    @NotBlank(message = "镜像名称不能为空")
    private String image;

    /** 本地不存在该镜像时自动拉取（配合 registryId 携带凭据） */
    private Boolean pullIfMissing;
    /** 拉取使用的镜像仓库 id（0=Docker Hub 内置匿名），为空则按镜像地址智能匹配 */
    private Long registryId;

    // ===== 基础 =====
    private String name;
    private String hostname;
    private String domainName;
    private Map<String, String> labels;

    // ===== 启动 =====
    /** 覆盖镜像 Entrypoint（--entrypoint） */
    private List<String> entrypoint;
    /** 覆盖镜像 CMD（docker run 尾部命令） */
    private List<String> command;
    /** 环境变量，KEY=VALUE 列表 */
    private List<String> env;
    private String workingDir;
    private String user;
    private Boolean tty;
    private Boolean stdinOpen;
    private Boolean init;
    private String stopSignal;
    /** --stop-timeout，单位秒 */
    private Integer stopTimeout;
    private HealthcheckSpec healthcheck;

    // ===== 网络 =====
    /** bridge / host / none / container:xxx / 自定义网络名或 id */
    private String networkMode;
    private List<String> networkAliases;
    /** --link，格式 container[:alias] */
    private List<String> links;
    /** --add-host，格式 hostname:ip */
    private List<String> extraHosts;
    private List<String> dns;
    private List<String> dnsSearch;
    private String macAddress;

    // ===== 端口 =====
    /** -P 发布所有暴露端口 */
    private Boolean publishAll;
    private List<PortBinding> portBindings;
    /** 仅暴露不发布，格式 8080/tcp */
    private List<String> exposedPorts;

    // ===== 存储 =====
    private List<VolumeMount> volumeMounts;
    /** --tmpfs，容器内路径列表 */
    private List<String> tmpfs;

    // ===== 资源 =====
    /** --memory，字节 */
    private Long memoryLimit;
    /** --memory-swap，字节 */
    private Long memorySwap;
    /** --memory-reservation，字节 */
    private Long memoryReservation;
    /** --cpus 换算后的 NanoCPUs（核数 × 1e9） */
    private Long cpuLimit;
    /** --cpu-shares */
    private Long cpuShares;
    /** --cpuset-cpus，如 0-2,4 */
    private String cpusetCpus;
    /** --shm-size，字节 */
    private Long shmSize;
    /** --pids-limit */
    private Long pidsLimit;
    /** --oom-kill-disable */
    private Boolean oomKillDisable;
    /** --oom-score-adj */
    private Integer oomScoreAdj;
    private List<UlimitSpec> ulimits;

    // ===== 安全 =====
    private Boolean privileged;
    /** --cap-add，如 SYS_ADMIN（CAP_ 前缀可省略） */
    private List<String> capAdd;
    private List<String> capDrop;
    private List<String> securityOpts;
    private List<String> groupAdd;
    /** --sysctl */
    private Map<String, String> sysctls;
    /** --device，宿主机设备路径 */
    private List<String> devices;
    /** 根文件系统只读（--read-only） */
    private Boolean readOnly;

    // ===== 生命周期 / 驱动 =====
    private String restartPolicy;
    private Boolean autoRemove;
    /** --log-driver，如 json-file / syslog */
    private String logDriver;
    /** --log-opt */
    private Map<String, String> logOpts;
    /** --pid/--ipc/--uts/--userns/--cgroup-parent/--runtime 等命名空间与驱动选项 */
    private String pidMode;
    private String ipcMode;
    private String utsMode;
    private String usernsMode;
    private String cgroupParent;
    private String runtime;

    // ===== 嵌套结构 =====

    @Data
    public static class PortBinding {
        /** 绑定地址，可空（-p ip:hostPort:containerPort） */
        private String hostIp;
        private String hostPort;
        private String containerPort;
        /** host 端口范围，如 8080-8090（可空） */
        private String hostPortRangeEnd;
        private String protocol = "tcp";
    }

    @Data
    public static class VolumeMount {
        /** bind: 宿主机路径；volume: 卷名；tmpfs: 可空 */
        private String source;
        private String target;
        private boolean readOnly = false;
        /** bind | volume | tmpfs，默认 bind */
        private String type = "bind";
    }

    @Data
    public static class UlimitSpec {
        private String name;
        private Long soft;
        private Long hard;
    }

    @Data
    public static class HealthcheckSpec {
        /** 测试命令，如 ["CMD-SHELL","curl -f http://localhost/ || exit 1"] */
        private List<String> test;
        private Long intervalSec;
        private Long timeoutSec;
        private Long startPeriodSec;
        private Integer retries;
    }
}
