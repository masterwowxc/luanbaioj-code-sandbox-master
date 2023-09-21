package com.Luanbai.sandbox.controller;

import com.Luanbai.sandbox.core.CodeSandboxFactory;
import com.Luanbai.sandbox.core.CodeSandboxTemplate;
import com.Luanbai.sandbox.model.ExecuteCodeRequest;
import com.Luanbai.sandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/codesandbox")
public class CodeSandboxController {

    @PostMapping("/execute")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest) {
        CodeSandboxTemplate sandboxTemplate = CodeSandboxFactory.getInstance(executeCodeRequest.getLanguage());
        return sandboxTemplate.executeCode(executeCodeRequest);
    }
}
