package com.Luanbai.sandbox;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.json.JSONUtil;
import com.Luanbai.sandbox.core.CodeSandboxTemplate;
import com.Luanbai.sandbox.core.JavaNativeCodeSandbox;
import com.Luanbai.sandbox.model.ExecuteCodeRequest;
import com.Luanbai.sandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

class JavaNativeCodeSandboxTest {

    @Test
    void executeCode() {
        CodeSandboxTemplate codeSandbox = new JavaNativeCodeSandbox ();
        String code = ResourceUtil.readStr("testcode/Main.java", StandardCharsets.UTF_8);
        ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest
                .builder()
                .inputList(Arrays.asList("1 2", "3 4"))
                .code(code)
                .build();
        ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
        System.out.println(JSONUtil.toJsonStr(executeCodeResponse));
    }
}
