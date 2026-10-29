package com.ae2webmanagement.ae2webmanagementmod.command;

/**
 * 待办命令的状态，对应 docs/design.md §4.3。
 */
public enum CommandState {
    /** 已受理、尚未提交给 AE2。 */
    PENDING,
    /** 已提交给 AE2（submitJob 成功）。 */
    SUBMITTED,
    /** 提交失败（如重新模拟后缺少材料等）。 */
    FAILED
}