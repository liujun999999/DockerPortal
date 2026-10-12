package com.dockerportal.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 总览数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardVO {
    private int runningContainers;
    private int stoppedContainers;
    private int totalImages;
    private int totalNetworks;
    private int totalVolumes;
    private boolean swarmMode;
    private int swarmNodes;
    private int swarmServices;
    private int swarmTasks;
}
