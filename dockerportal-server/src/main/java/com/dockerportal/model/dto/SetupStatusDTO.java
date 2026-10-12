package com.dockerportal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安装状态，供前端路由守卫判断是否跳到安装向导
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetupStatusDTO {

    /** 尚无任何用户且初始化入口开启，需要走安装向导 */
    private Boolean needsSetup;

    /** 数据目录此前为空：JWT 密钥已由本次启动随机生成落盘 */
    private Boolean freshInstall;

    /** 初始化入口是否被 DP_SETUP_ENABLED 关闭 */
    private Boolean setupEnabled;
}
