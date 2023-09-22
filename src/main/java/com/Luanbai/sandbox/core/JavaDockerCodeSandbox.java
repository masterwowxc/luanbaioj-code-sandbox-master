package com.Luanbai.sandbox.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.Luanbai.sandbox.model.ExecuteCodeRequest;
import com.Luanbai.sandbox.model.ExecuteCodeResponse;
import com.Luanbai.sandbox.model.ExecuteMessage;
import com.Luanbai.sandbox.model.JudgeInfo;
import com.Luanbai.sandbox.utils.ProcessUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static cn.hutool.core.lang.Console.log;


/**
 * java本机代码沙箱
 *
 * @author Luanbai
 * @since 2023/08/21
 */
@Slf4j
@Service
public class JavaDockerCodeSandbox implements CodeSandbox {

    private static final String PREFIX = File.separator + "java";

    private static final String GLOBAL_CODE_DIR_PATH = File.separator + "tempCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = File.separator + "Main.java";

    public static final Long TIME_OUT = 5000L;

    /**
     * 第一次拉取
     */
    private static boolean FIRST_PULL = true;

    //@Resource
    //private DockerDao dockerDao;


    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        if (executeCodeRequest==null){
            return new ExecuteCodeResponse();
        }
        // 1.获取到传入的代码，写到文件中
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String userDir = System.getProperty("user.dir");
        String globalCodePath = userDir + GLOBAL_CODE_DIR_PATH;
        // 2.判断全局路径是否存在，没有则新建
        if (!FileUtil.exist(globalCodePath)) {
            FileUtil.mkdir(globalCodePath);
        }

        // 3.存放用户代码
        String userCodeParentPath = globalCodePath + PREFIX + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString (code, userCodePath, StandardCharsets.UTF_8);


        // 4.编译代码
        try {
            String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath ());
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            //获取编译时，cmd中输出的信息
            ExecuteMessage executeMessage = ProcessUtil.handleProcessMessage (compileProcess, "编译");
            log("编译信息:" + executeMessage);
        } catch (IOException e) {
            return errorResponse(e);
        }
        // 创建docker连接
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 5.创建容器把文件放到docker中
		//拉取镜像
        String imgage = "openjdk:8-alpine";
        if (FIRST_PULL) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(imgage);
            //回调函数 查询输出的信息
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    log("下载镜像:" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();  //阻塞 直到下载完成才进行下一步
            } catch (InterruptedException e) {
                log("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }

        log("下载完成");

		//创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(imgage);
        //设置容器配置
        HostConfig hostConfig = new HostConfig();
        //设置内存
        hostConfig.withMemory(100 * 1000 * 1000L);
        //设置cpu数
        hostConfig.withCpuCount(1L);
        //容器挂载目录
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));
        //容器响应信息
        CreateContainerResponse createContainerResponse = containerCmd
                .withHostConfig(hostConfig)
                //禁用网络
                .withNetworkDisabled(true)
                //禁止在root目录写文件
                .withReadonlyRootfs(true)
                //开启交互的容器
                .withAttachStderr(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        log(createContainerResponse);
        String containerId = createContainerResponse.getId();
        //启动容器
        dockerClient.startContainerCmd(containerId).exec();

        //docker exec 容器name java -cp /app Main
        //进入容器执行命令
        long maxTime = 0;
        //执行命令获取结果
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String input : inputList) {
            //计时
            String[] inputArgsArray = input.split (" ");
            String[] cmdArray = ArrayUtil.append (new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
            //String[] cmd = ArrayUtil.append(new String[]{"java", "-Dfile.encoding=UTF-8", "-cp", "/app", "Main"}, input.split(" "));
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd (containerId)
                    //执行运行命令
                    .withCmd (cmdArray)
                    .withAttachStderr (true)
                    .withAttachStdin (true)
                    .withAttachStdout (true)
                    .exec ();
            log ("创建执行命令：" + execCreateCmdResponse);
            ExecuteMessage executeMessage = new ExecuteMessage ();
            //String execId = dockerDao.executeCreateCmd(containerId, cmd).getId();
            final String[] message = {null};
            final String[] errorMessage = {null};
            long time = 0L;
            //判断是否超时,默认就是超时
            final boolean[] timeout = {true};
            String execId = execCreateCmdResponse.getId ();

            ByteArrayOutputStream resultStream = new ByteArrayOutputStream ();
            ByteArrayOutputStream errorResultStream = new ByteArrayOutputStream ();
            //回调函数
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback () {

                @Override
                public void onComplete () {
                    //如果执行完成，则表示没超时
                    timeout[0] = false;
                    super.onComplete ();
                }

                @Override
                public void onNext (Frame frame) {
                    //并且通过 StreamType 来区分标准输出和错误输出。
                    StreamType streamType = frame.getStreamType ();
                    byte[] payload = frame.getPayload ();
                    if (StreamType.STDERR.equals (streamType)) {
                        try {
                            errorResultStream.write (payload);
                        } catch (IOException e) {
                            throw new RuntimeException (e);
                        }
                    } else {
                        try {
                            resultStream.write (payload);
                        } catch (IOException e) {
                            throw new RuntimeException (e);
                        }
                    }
                }
            };
            final long[] maxMemory = {0L};
            //获取占用内存
            StatsCmd statsCmd = dockerClient.statsCmd (containerId);
            ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec (new ResultCallback<Statistics> () {
                @Override
                public void onNext (Statistics statistics) {
                    Long usageMemory = statistics.getMemoryStats ().getUsage ();
                    log ("内存占用" + usageMemory);
                    if (usageMemory != null) {
                        maxMemory[0] = Math.max (usageMemory, maxMemory[0]);

                    }
                }

                @Override
                public void onStart (Closeable closeable) {

                }

                @Override
                public void onError (Throwable throwable) {

                }

                @Override
                public void onComplete () {

                }

                @Override
                public void close () throws IOException {

                }
            });
            //启动监控 监控内存等信息
            statsCmd.exec (statisticsResultCallback);
            statsCmd.close ();
            try {
                //启动计时
                StopWatch stopWatch = new StopWatch ();
                stopWatch.start ();
                dockerClient.execStartCmd (execId)
                        .exec (execStartResultCallback)
                        .awaitCompletion (TIME_OUT, TimeUnit.MILLISECONDS);  //设置超时时间
                stopWatch.stop ();
                time = stopWatch.getLastTaskTimeMillis ();
                statsCmd.close ();
            } catch (InterruptedException e) {
                log ("代码执行异常");
                throw new RuntimeException (e);
            }

            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            executeMessage.setTime(time);
            executeMessage.setMemory(maxMemory[0]);
            executeMessageList.add(executeMessage);
        }

        //删除容器
        dockerClient.stopContainerCmd(containerId).exec();
        dockerClient.removeContainerCmd(containerId).exec();
        log("删除成功");
        //封装结果
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();

        long maxMemory = 0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                //错误执行信息
                executeCodeResponse.setMessage(errorMessage);
                //执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            //正确的成功信息
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            Long memory = executeMessage.getMemory();
            if (time != null) {
                //取出来的时间进行比较 取最大的那个
                maxTime = Math.max(maxTime, time);
            }
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        //正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        executeCodeResponse.setOutputList(outputList);
        //5.文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

    private ExecuteCodeResponse errorResponse(Throwable e) {
        return ExecuteCodeResponse
                .builder()
                .outputList(new ArrayList<>())
                .message(e.getMessage())
                .judgeInfo(new JudgeInfo())
                .status(2)
                .build();
    }
}
