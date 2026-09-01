package com.jmzs.app.data

/** 平台预设线路（文档 2024-11-16 版），用户可在登录页自定义 */
val DEFAULT_SERVERS = listOf(
    "api.haozhuma.com",
    "api.haozhuyun.com",
)

/** 运营商参数代码表（文档 2024-10-25 版，仅列出可用的） */
data class Operator(val code: Int, val name: String)

val OPERATORS = listOf(
    Operator(1, "中国移动"),
    Operator(5, "中国联通"),
    Operator(9, "中国电信"),
    Operator(14, "中国广电"),
    Operator(16, "虚拟运营商"),
)

/** 省份代码表（文档 2022-11-26 版） */
data class Province(val code: String, val name: String)

val PROVINCES = listOf(
    Province("11", "北京"),
    Province("12", "天津"),
    Province("13", "河北"),
    Province("14", "山西"),
    Province("15", "内蒙古"),
    Province("21", "辽宁"),
    Province("22", "吉林"),
    Province("23", "黑龙江"),
    Province("31", "上海"),
    Province("32", "江苏"),
    Province("33", "浙江"),
    Province("34", "安徽"),
    Province("35", "福建"),
    Province("36", "江西"),
    Province("37", "山东"),
    Province("41", "河南"),
    Province("42", "湖北"),
    Province("43", "湖南"),
    Province("44", "广东"),
    Province("45", "广西"),
    Province("46", "海南"),
    Province("50", "重庆"),
    Province("51", "四川"),
    Province("52", "贵州"),
    Province("53", "云南"),
    Province("54", "西藏"),
    Province("61", "陕西"),
    Province("62", "甘肃"),
    Province("63", "青海"),
    Province("64", "宁夏"),
    Province("65", "新疆"),
)

/** 号码类型 */
data class Ascription(val code: Int?, val name: String)

val ASCRIPTIONS = listOf(
    Ascription(null, "不限"),
    Ascription(1, "只取虚拟"),
    Ascription(2, "只取实卡"),
)

/** 验证码轮询间隔选项（秒） */
val POLL_INTERVALS = listOf(10, 15, 30)
