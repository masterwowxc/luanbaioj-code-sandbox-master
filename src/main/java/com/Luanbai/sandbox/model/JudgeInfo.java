package com.Luanbai.sandbox.model;

import lombok.Data;

/**
 * 判题信息
 *
 * @author Luanbai
 * @since 2023/08/08
 */
@Data
public class JudgeInfo {

    /**
     * 程序执行信息
     */
    private String message;

    /**
     * 消耗内存
     */
    private Long memory;

    /**
     * 消耗时间（KB）
     */
    private Long time;
}
