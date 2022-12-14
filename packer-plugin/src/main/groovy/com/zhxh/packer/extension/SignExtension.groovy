package com.zhxh.packer.extension

/**
 * 签名配置
 *
 * @author zhxh
 */
class SignExtension {

    /**
     * 签名文件路径
     */
    String keystorePath
    /**
     * 签名文件密码
     */
    String keystorePassword
    /**
     * 签名文件alias
     */
    String alias
    /**
     * 签名文件alias密码
     */
    String aliasPassword
}