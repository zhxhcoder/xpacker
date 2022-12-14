
# 自己动手实现自动打包，上传下载服务，前端展示
https://www.jianshu.com/p/caa2fed9cd74

## 版本演变

- Version1.x，Python脚本

- Version2.x，本地的Gradle脚本

- Version3.x，Gradle插件


因为之前用Python脚本，需要服务器也安装Python环境，现在改进了，通过Gradle Task自动打包、加固、并上传到指定FTP地址，供其他人员使用APK，可以节省开发人员的打包时间、方便测试等同学使用最新apk、可以在FTP上查找历史版本APK。

## xpacker

参考文章，之前是用Python写成的打包、加固、上传脚本，最好为了更具适应性，写成了gradle插件

[![license](http://img.shields.io/badge/license-MIT-brightgreen.svg?style=flat)](https://github.com/zhxhcoder/xpacker/blob/master/LICENSE)
[![Release Version](https://img.shields.io/badge/release-3.1.0-red.svg)](https://github.com/zhxhcoder/xpacker/releases)

## Feature

- [x] 支持variants。

- [x] 支持自定义ftp。

- [x] 支持自定义上传路径。

- [x] 支持variant。

- [x] 支持360加固。

- [x] 支持多渠道打包。

- [x] 支持美团walle多渠道打包、360加固多渠道打包。

- [x] 支持上传mapping
  
- [x] 支持上传logs
  
- [x] 支持上传sdk-dependencies

## 引用

```groovy
dependencies {
    classpath "com.zhxh.packer:packer:3.1.0"
}
```

## 用法

1. 将`packer-demo`中的`jiagu`目录复制到自己工程的`app`目录下。
    `jiagu.sh`是加固脚本，`walle-cli-all.jar`是[美团walle](https://github.com/Meituan-Dianping/walle)多渠道打包工具。

2. 在主工程中添加如下配置代码。

```groovy
apply plugin: 'com.zhxh.packer'

packer {

    jiagu {
        userName = '360加固用户名'
        password = '360加固密码'
        jiaguPath = '~/360jiagubao_mac/jiagu' // 360加固工具路径
        // channelsPath = '/***/channels.txt' // 多渠道配置文件，参考360加固多渠道配置模板
        channelsPath = "${project.projectDir}/walle_channels" // walle多渠道配置文件
        useWalle = true // 默认使用360加固，如果为true则使用美团walle进行多渠道打包
    }

    sign {
        keystorePath = '/***/sign.keystore' // 签名
        keystorePassword = '***' // 签名文件密码
        alias = '***' // 别名
        aliasPassword = '***' // 别名密码
    }

    ftp {
        ftpUserName = '***' // ftp用户名
        ftpPassword = 'xxxx' // ftp密码
        ftpUrl = 'ftp://***/app/' // ftp地址
        autoCreateDir = false // false直接传到ftpUrl目录，true会创建 projectName/versionName/ 目录
        publishDir = "packer-demo-release" // 加固包上传目录(publish***Apks)task.
        uploadDir = "packer-demo-beta" // 未加固包上传目录(upload***Apk)task.
        uploadMapping = true // 是否上传build/outputs/mapping文件，默认上传
        uploadLogs = true // 是否上传build/outputs/logs，默认上传
        uploadSdkDependencies = true // 是否上传build/outputs/sdk-dependencies，默认上传
    }

}
```

Sync Gradle之后在`Android Studio->Gradle->app->packer`task组中可以查看并使用packer所有task。

`task说明：`以`upload`开头的task是未加固的，每次会上传一个apk。以`publish`开头的task会进行360加固、重新签名、多渠道打包，会上传多个apk，取决于`channels.txt`中配置多少个渠道。

## License

[![license](http://img.shields.io/badge/license-MIT-brightgreen.svg?style=flat)](https://github.com/zhxhcoder/xpacker/blob/master/LICENSE)
