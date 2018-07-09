/**
 * Created by Summer on 2018/3/7.
 */
package com.iscas.yf.IntelliPipeline

//@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7')
//import groovyx.net.http.HTTPBuilder
//import static groovyx.net.http.ContentType.*
//import static groovyx.net.http.Method.*



public class IntelliAgent{

    def scripts
    def currentBuild

    // 构造函数
    IntelliAgent(scripts, currentBuild){
        this.scripts = scripts
        this.currentBuild = currentBuild
    }

    def keepGetting() {

        // 持续发送HTTP请求的指示器
        def stepNumber = 1

        def flag = true

        // 没有执行step，request type为initializing
        def requestType = "START"

        // 将代码片段放入node代码段中
//        scripts.node{
//            logger "node block entering"
//
//            try {
//                logger "Try-catch block entering"
//                // TODO: 如何接受返回来的字符串？
//                // 创建一个Http对象，向服务端发送请求
//                def http = new HTTPBuilder('http://39.104.105.27:8888')
//                http.request(GET, TEXT) { req ->
//                    // 设置url相关信息 - http://39.104.105.27:8888/IntelliPipeline/upload
//                    uri.path='/IntelliPipeline/upload'
//
//                    // 设置请求头信息
//                    headers.'User-Agent' = "Mozilla/5.0 Firefox/3.0.4"
//
//                    // 设置成功响应的处理闭包
//                    response.success= { resp,reader->
//                        // 成功响应返回的200状态码
//                        assert resp.statusLine.statusCode == 200
//
//                        // 打印返回的详细信息
//                        logger resp.status
//                        logger resp.statusLine.statusCode
//                        logger resp.headers.'content-length'
//
//                        logger "INTENTION! RETURNED TEXT IS BELOW!"
//                        logger reader.text
//
//                        // 把返回来的字符串赋值给变量info
//                        info = reader.text
//
//                        // TODO: 测试看能否收到成功返回就执行scripts中的步骤
//                        myExecutor.execution()
//
//                        // 执行5个stage就退出
//                        count += 1
//                        if(count == 5) {
//                            flag = false
//                        }
//
//                        logger "My response handler got response: ${resp.statusLine}"
//                        logger "Response length: ${resp.headers.'Content-Length'}"
//                        System.out << reader // print response stream
//                    }
//
//                    // 404
//                    response.'404' = {
//                        logger 'not found'
//                    }
//
//                    // 401
//                    http.handler.'401' = { resp ->
//                        logger "Access denied"
//                    }
//
//                    // 未根据响应码指定的失败处理闭包
//                    response.failure = { logger "Unexpected failure: ${resp.statusLine}" }
//                    logger 'End of sending request'
//                }
//            } catch (err) {
//                logger "Error occurred:" + err
//                throw err
//            }
//        }

        try {
            while(flag){

//                this.scripts.steps.echo("while loop")
                // changeSets，只需要在开始构建时发送一次
                // 直接把changeSets这个对象发回去，客户端再进行处理(ArrayList), 如何传输一个对象？不可行
                // changeSets是两次build之间

                // Jenkins当前构建的控制台输出. consoleStr是字符串， 验证可行
//                def consoleOutput = this.scripts.currentBuild.rawBuild.getLogInputStream()
//                byte[] bytes = new byte[consoleOutput.available()]
//                consoleOutput.read(bytes);
//                String consoleStr = new String(bytes)
//                this.scripts.steps.echo("consoleStr: " + consoleStr);

                // 当前构建的持续时间，单位毫秒
                def durationTime = this.scripts.currentBuild.duration

                def currentResult = this.scripts.currentBuild.currentResult
                this.scripts.steps.echo("currentResult: " + currentResult)

                def buildNumber = this.scripts.currentBuild.number

                def body = """ """

                if(requestType == "INIT"){
                    def String commitSet = processCommitSet()

                    body = """
                        {"requestType": "$requestType",
                         "stepNumber": "$stepNumber",
                         "buildNumber": "$buildNumber",
                         "currentResult": "$currentResult",
                         "commitSet": "$commitSet",
                         "durationTime": "$durationTime"}
                    """
                } else {
                    body = """
                        {"requestType": "$requestType",
                         "stepNumber": "$stepNumber",
                         "buildNumber": "$buildNumber",
                         "currentResult": "$currentResult",
                         "durationTime": "$durationTime"}
                    """
                }

                def postResponseContent = executePostRequest(body)

                // 发送POST Request
//                def response = this.scripts.steps.httpRequest(
//                        acceptType:'APPLICATION_JSON',
//                        contentType:'APPLICATION_JSON',
//                        httpMode:'POST',
//                        requestBody: body,
//                        consoleLogResponseBody: true,
//                        url: "http://39.104.105.27:8888/IntelliPipeline/build_data/upload")

                // 抛弃使用HttpRequest Plugin, 改为Groovy原生方法
//                def post = new URL("http://39.104.105.27:8888/IntelliPipeline/build_data/upload").openConnection();
//
//                post.setRequestMethod("POST")
//                post.setDoOutput(true)
//                post.setRequestProperty("Content-Type", "application/json")
//                post.getOutputStream().write(body.getBytes("UTF-8"))
//                def postResponseCode = post.getResponseCode()
//                def postResponseContent = ''
//                if(postResponseCode.equals(200)){
//                    postResponseContent = post.getInputStream().getText();
//                } else {
//                    continue
//                }

//                this.scripts.steps.echo("Response: $postResponseContent")

                def parsedBody = this.scripts.steps.readJSON(text: postResponseContent)

                // 先获取返回的decision
                def String decision = parsedBody.decisionType
                // assert decision instanceof String
//                this.scripts.steps.echo("decision: " + decision)

                // 先处理decision的各种情况
                if(decision.equals("NEXT")){
                    stepNumber++
                }
                // build流程结束
                else if(decision.equals("END")){
                    flag = false
                    break;
                }
                // 跳过此次build, 只执行一个Step: mail step
                else if(decision.equals("SKIP_BUILD")){
                    stepNumber = 9999;
                }
                // 重试当前步骤， 不作操作继续请求同一个step
                else if(decision.equals("RETRY")){

                }
                // 跳过当前step的执行
                else if(decision.equals("SKIP_STEP")){
                    stepNumber++
                    continue;
                }

                // 发送到converter进行解析, 分别获取stepName和stepParams
                def Map<String, Object> stepParams = parsedBody.params
                // assert stepParams instanceof Map<String, Object>

                def String stepName = parsedBody.stepName
                // assert stepName instanceof String
//                this.scripts.steps.echo("stepName: " + stepName)

                // 返回码从100-399，200表示成功返回。状态码不是String类型，是int类型

                if(postResponseContent != ""){
                    // 调用invokeMethod方法执行step, node也可以赋予参数实现分布式执行
                    executeStep(stepName, stepParams)

                    if(stepName.equals("git")) {
                        requestType = "INIT"
                    } else {
                        requestType = "RUNNING"
                    }
                } else {
                    // 出现网络错误，暂时退出. 应重发
                    this.scripts.steps.echo "Network connection error occurred"
                    requestType = "NETWORK_ERROR"
                    // 5s后重试
                    sleep(5000);
                }
            }
        } catch(err) {
            this.scripts.steps.echo("An error occurred: " + err)
            // Step执行出错了
            // requestType = "error"
            requestType = "FAILURE"
        }
    }

    @NonCPS
    def processCommitSet(){
        def changeSets = this.scripts.currentBuild.changeSets
        def String commitSet = "";
        // 只在INIT的时候处理一次changeSets
        for(int i = 0; i < changeSets.size(); i++){
            def entries = changeSets[i].items
            for(int j = 0; j < entries.length; j++){
                def entry = entries[j]
                // 将所有的commit都加入到changeLog中, 不同的commit用[]分割
                commitSet += "[${entry.commitId} : ${entry.author} : ${entry.msg}] "
            }
        }
        return commitSet
    }

    @NonCPS
    def executePostRequest(body){
        def post = new URL("http://localhost:8888/IntelliPipeline/build_data/upload").openConnection();
        post.setRequestMethod("POST")
        post.setDoOutput(true)
        post.setRequestProperty("Content-Type", "application/json")
        post.getOutputStream().write(body.getBytes("UTF-8"))
        def postResponseCode = post.getResponseCode()
        def postResponseContent = ''
        if(postResponseCode.equals(200)){
            postResponseContent = post.getInputStream().getText();
            return postResponseContent
        }
        return ""
    }

    def executeStep(String stepName, Map<String, Object> stepParams){
        // 调用invokeMethod方法执行step, node也可以赋予参数实现分布式执行
        this.scripts.steps.node(){
            try{
                this.scripts.steps.invokeMethod(stepName, stepParams)
            } catch(err) {
                this.currentBuild.result = 'FAILURE'
                throw err
            }
        }
    }


}



