package com.dockerportal.exception;

import com.dockerportal.docker.DockerClientManager;
import com.github.dockerjava.api.model.LocalNodeState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Swarm 控制面错误的统一分类器。
 *
 * Docker 对「未初始化集群 / Worker 节点无管理权限 / Manager 被锁定」返回的报错文字相似且不可靠，
 * 因此以 info 接口的 {@link LocalNodeState} 为准判定，文字仅作回退。
 *
 * 统一映射为引导码，供前端 {@code SwarmGuide} 组件渲染（不弹红色报错）：
 * 40901 = 未初始化（可引导 init），40902 = Worker 节点（无 manager 权限），40903 = Manager 锁定。
 *
 * 由 {@link GlobalExceptionHandler}（docker-java 类型化异常）与
 * {@code SwarmResourceService#rawRequest}（手写 HTTP，如 Configs 接口）共同复用，避免逻辑漂移。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SwarmErrorClassifier {

    private final DockerClientManager dockerClientManager;

    /** 是否疑似 Swarm 控制面相关错误（Docker 这类报错统一含 "swarm" 字样） */
    public boolean isSwarmError(String message) {
        return message != null && message.toLowerCase().contains("swarm");
    }

    /**
     * 依据环境的 LocalNodeState 分类；envId 为空或查询失败时按文字回退。
     * @return 40901 / 40902 / 40903
     */
    public int classify(Long envId, String message) {
        if (envId != null) {
            try {
                var swarm = dockerClientManager.getClient(envId).infoCmd().exec().getSwarm();
                LocalNodeState state = swarm == null ? null : swarm.getLocalNodeState();
                if (state == LocalNodeState.LOCKED) return 40903;
                if (state == LocalNodeState.INACTIVE || state == LocalNodeState.EMPTY) return 40901;
                // Worker 判定：Docker 29.x 起 worker 也报 ACTIVE，与 manager 的唯一区别是 ControlAvailable=false；
                // 旧版本 worker 报 PENDING。两者都表示「已加入集群但无管理权限」，归为 40902。
                if (state == LocalNodeState.PENDING) return 40902;
                if (state == LocalNodeState.ACTIVE && !Boolean.TRUE.equals(swarm.getControlAvailable())) return 40902;
            } catch (Exception ex) {
                log.debug("Cannot resolve swarm state for envId={}, fallback to message matching", envId, ex);
            }
        }
        if (message != null) {
            String lower = message.toLowerCase();
            // 文字兜底：daemon 明确说「不是 manager」或「未加入集群」，都按 worker/无权限处理
            if (lower.contains("not a swarm manager")) return 40902;
            if (message.contains("Error 3: This node is not part of a swarm")) return 40902;
        }
        return 40901;
    }

    /** 引导码对应的用户可读文案 */
    public String friendlyMessage(int code) {
        return switch (code) {
            case 40902 -> "当前节点是 Swarm Worker 节点，无集群管理权限。请在 Manager 节点获取令牌后加入集群，或切换到 Swarm Manager 所在的环境。";
            case 40903 -> "当前 Swarm 集群的 Manager 已被锁定，需要输入解锁密钥（docker swarm unlock）后才能管理。";
            default -> "当前 Docker 环境尚未初始化 Swarm 集群，可在此页面一键初始化。";
        };
    }
}
