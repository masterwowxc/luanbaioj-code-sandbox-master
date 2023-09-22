package com.Luanbai.sandbox.controller;

import com.Luanbai.sandbox.core.CodeSandboxFactory;
import com.Luanbai.sandbox.core.CodeSandboxTemplate;
import com.Luanbai.sandbox.core.JavaDockerCodeSandbox;
import com.Luanbai.sandbox.model.ExecuteCodeRequest;
import com.Luanbai.sandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/codesandbox")
public class CodeSandboxController {

    //用一个字符串来保证接口调用的安全性，只要对面传入这个字符串，那就代表可以调用
    public static final String AUTH_REQUEST_HEADER="auth";
    public static final String AUTH_REQUEST_SECRET="secretKey";

    private JavaDockerCodeSandbox javaDockerCodeSandbox;

    @PostMapping("/execute")
    public ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest,                                 HttpServletRequest request,
                                           HttpServletResponse response) {
        CodeSandboxTemplate sandboxTemplate = CodeSandboxFactory.getInstance(executeCodeRequest.getLanguage());

        //基本认证
        String authHeader = request.getHeader(AUTH_REQUEST_HEADER);
        if (!AUTH_REQUEST_SECRET.equals(authHeader)){
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest==null){
            throw  new RuntimeException("请求参数为空");
        }
        //return sandboxTemplate.executeCode(executeCodeRequest);
        return javaDockerCodeSandbox.executeCode(executeCodeRequest);
    }
}
