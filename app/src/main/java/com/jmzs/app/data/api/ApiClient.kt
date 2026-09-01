package com.jmzs.app.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 接口调用失败（网络错误或平台返回 code != 0） */
class ApiException(message: String) : Exception(message)

object ApiClient {

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 向 {server}/sms/ 发起 GET 请求并返回原始响应文本。
     * server 支持以下写法：api.haozhuma.com、https://api.haozhuma.com、
     * http://127.0.0.1:8080（自定义线路，不带协议时默认 https）。
     */
    suspend fun get(server: String, params: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val raw = server.trim().trimEnd('/')
            if (raw.isEmpty()) throw ApiException("服务器地址为空")

            val scheme = if (raw.startsWith("http://")) "http" else "https"
            val hostPort = raw.removePrefix("https://").removePrefix("http://")
            val (host, port) = if (hostPort.contains(':')) {
                val parts = hostPort.split(':', limit = 2)
                parts[0] to parts[1].toIntOrNull()
            } else {
                hostPort to null
            }
            if (host.isEmpty()) throw ApiException("服务器地址格式不正确")

            val builder = HttpUrl.Builder()
                .scheme(scheme)
                .host(host)
                .addPathSegments("sms/")
            port?.let { builder.port(it) }
            params.forEach { (key, value) -> builder.addQueryParameter(key, value) }

            val request = Request.Builder()
                .url(builder.build())
                .get()
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw ApiException("HTTP ${response.code}")
                    }
                    if (body.isBlank()) throw ApiException("服务器返回空响应")
                    body
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                throw ApiException("网络连接失败：${e.message ?: "请检查服务器地址和网络"}")
            }
        }
}
