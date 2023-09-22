package com.Luanbai.sandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 进程执行信息
 *
 * @author Luanbai
 * @since 2023/08/25
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExecuteMessage {

    //错误码
    private Integer exitCode;

    //正常输出信息
    private String message;

    //异常输出信息
    private String errorMessage;

    //运行代码的时间
    private Long time;

    //运行内存
    private Long memory;
}
