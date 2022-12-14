package com.zhxh.packer.task

import com.android.build.gradle.AppExtension
import com.zhxh.packer.extension.PackerExtension
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecSpec

import java.nio.file.Paths

/**
 * @author zhxh
 */
class TaskCreator {

    static final TASK_PREFIX_PUBLISH = "publish"
    static final TASK_PREFIX_UPLOAD = "upload"
    static final TASK_SUFFIX_APK = "Apk"

    /**
     * 创建task
     * @param project
     * @param isPublish true对apk进行加固、重新签名、多渠道打包，false不进行加固。
     */
    static void createTasks(Project project, boolean isPublish) {
        project.afterEvaluate {
            def appExt = project.extensions.getByType(AppExtension)
            // 遍历variant
            appExt.applicationVariants.forEach { variant ->
                def variantName = variant.name.capitalize()
                def taskPrefix = isPublish ? TASK_PREFIX_PUBLISH : TASK_PREFIX_UPLOAD
                def taskSuffix = TASK_SUFFIX_APK
                // 任务名
                def taskName = taskPrefix + variantName + taskSuffix
                // 创建task
                project.tasks.create(taskName) {
                    // 依赖assembleRelease/assembleDebug...
                    def dependsTask = "assemble" + variantName
                    dependsOn(dependsTask)
                    // 指定任务组
                    group = "packer"
                    doLast {
                        def packerExt = project.extensions.getByType(PackerExtension)
                        def apkDirectory = packerExt.apkDirectory
                        if (apkDirectory == null || apkDirectory == "") {
                            apkDirectory = "${project.projectDir}/build/outputs/apk/" + variant.getDirName() + "/"
                            project.logger.lifecycle("> Packer: apk directory is : $apkDirectory")
                            new File(apkDirectory).list().each {
                                if (it.endsWith(".apk")) {
                                    def apkFilePath = apkDirectory + it

                                    if (isPublish) {
                                        // 加固包处理
                                        handleJiaguApk(project, apkFilePath, variant.getDirName())
                                    } else {
                                        // 不加固包处理
                                        uploadApk(project, isPublish, apkFilePath, variant.getDirName())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    static void execAndLog(Project project, GString command) {
        def process = command.execute()
        def processOutput = new StringBuilder()
        def processError = new StringBuilder()
        process.waitForProcessOutput(processOutput, processError)
        project.logger.lifecycle(processOutput.toString())
        project.logger.error(processError.toString())
    }

    static void handleJiaguApk(Project project, GString apkFilePath, String variantName) {
        def packerExt = project.extensions.getByType(PackerExtension)
        def jiaguUserName = packerExt.jiagu.userName
        def jiaguPassword = packerExt.jiagu.password
        def jiaguChannelsPath = packerExt.jiagu.channelsPath
        def jiaguPath = packerExt.jiagu.jiaguPath
        def keystorePath = packerExt.sign.keystorePath
        def keystorePassword = packerExt.sign.keystorePassword
        def alias = packerExt.sign.alias
        def aliasPassword = packerExt.sign.aliasPassword
        def useWalle = packerExt.jiagu.useWalle
        def out = new ByteArrayOutputStream()
        def walleJarPath = "${project.projectDir}/jiagu/walle-cli-all.jar"

        def appExt = project.extensions.getByType(AppExtension)
        def zipalign = appExt.sdkDirectory.absolutePath + "/build-tools/" + project.android.getBuildToolsVersion() + "/" + "zipalign"
        def apksigner = appExt.sdkDirectory.absolutePath + "/build-tools/" + project.android.getBuildToolsVersion() + "/" + "apksigner"

        // jiagu.sh apk路径 apk输出路径
        def cmd = "./jiagu/jiagu.sh $jiaguPath $jiaguUserName $jiaguPassword $apkFilePath $jiaguChannelsPath $keystorePath $keystorePassword $alias $aliasPassword $useWalle $walleJarPath $zipalign $apksigner"
        project.exec {
            ExecSpec execSpec ->
                executable 'bash'
                args '-c', cmd
                standardOutput = out
        }
        println(out.toString())

        def outputPath = "$jiaguPath/output/$jiaguUserName/"

        project.logger.lifecycle("> Packer: 加固apk输出路径：$outputPath")

        new File(outputPath).list().each {
            if (it.endsWith(".apk")) {
                uploadApk(project, true, outputPath + it, variantName)
            }
        }
    }

    static void uploadApk(Project project, boolean isPublish, String apkFilePath, String variantName) {
        def appExt = project.extensions.getByType(AppExtension)
        def packerExt = project.extensions.getByType(PackerExtension)
        def ftpUserName = packerExt.ftp.ftpUserName
        def ftpPwd = packerExt.ftp.ftpPassword
        def uploadMapping = packerExt.ftp.uploadMapping
        def uploadLogs = packerExt.ftp.uploadLogs
        def uploadSdkDependencies = packerExt.ftp.uploadSdkDependencies
        def ftpUrl = ""
        if (packerExt.ftp.ftpUrl != null) {
            ftpUrl = packerExt.ftp.ftpUrl
        }
        def rootDir = project.getRootProject().name
        if (isPublish) {
            if (packerExt.ftp.publishDir != null) {
                rootDir = packerExt.ftp.publishDir
            }
        } else {
            if (packerExt.ftp.uploadDir != null) {
                rootDir = packerExt.ftp.uploadDir
            }
        }
        def realFtpUrl
        if (packerExt.ftp.autoCreateDir) {
            realFtpUrl = ftpUrl + rootDir + "/v" + appExt.defaultConfig.versionName + "/"
        } else {
            realFtpUrl = ftpUrl
        }

        project.logger.lifecycle("> Packer: 上传加固apk：" + apkFilePath)

        // 上传文件命令(如果目录不存在自动创建）
        def command = "curl -u $ftpUserName:$ftpPwd -T $apkFilePath $realFtpUrl --ftp-create-dirs"
        execAndLog(project, command)
        project.logger.lifecycle("> Packer: ftp路径：" + realFtpUrl)

        // 上传mapping
        if (uploadMapping) {
            project.logger.lifecycle("> Packer: 上传mapping...")
            def mappingPath = "${project.buildDir}/outputs/mapping/${variantName}/"
            def ftpMappingPath = ftpUrl + rootDir + "/v" + appExt.defaultConfig.versionName + "/mapping/"
            new File(mappingPath).list().each {
                def mappingCommand = "curl -u $ftpUserName:$ftpPwd -T $mappingPath/$it $ftpMappingPath --ftp-create-dirs"
                execAndLog(project, mappingCommand)
            }
        }

        // 上传logs
        if (uploadLogs) {
            project.logger.lifecycle("> Packer: 上传logs...")
            def logsPath = "${project.buildDir}/outputs/logs/"
            def ftpLogsPath = ftpUrl + rootDir + "/v" + appExt.defaultConfig.versionName + "/logs/"
            new File(logsPath).list().each {
                if (it.contains(variantName)) {
                    def logsCommand = "curl -u $ftpUserName:$ftpPwd -T $logsPath/$it $ftpLogsPath --ftp-create-dirs"
                    execAndLog(project, logsCommand)
                }
            }
        }

        // 上传sdk-dependencies
        if (uploadSdkDependencies) {
            project.logger.lifecycle("> Packer: 上传sdk-dependencies...")
            def sdkDependenciesPath = "${project.buildDir}/outputs/sdk-dependencies/${variantName}/"
            def ftpSdkDependenciesPath = ftpUrl + rootDir + "/v" + appExt.defaultConfig.versionName + "/sdk-dependencies/"
            new File(sdkDependenciesPath).list().each {
                def sdkDependenciesCommand = "curl -u $ftpUserName:$ftpPwd -T $sdkDependenciesPath/$it $ftpSdkDependenciesPath --ftp-create-dirs"
                execAndLog(project, sdkDependenciesCommand)
            }
        }
    }
}