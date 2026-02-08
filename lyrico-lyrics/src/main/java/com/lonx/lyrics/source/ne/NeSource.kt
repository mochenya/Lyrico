package com.lonx.lyrics.source.ne

import android.content.Context
import android.util.Base64
import android.util.Log
import com.lonx.lyrics.model.LyricsResult
import com.lonx.lyrics.model.SearchSource
import com.lonx.lyrics.model.SongSearchResult
import com.lonx.lyrics.model.Source
import com.lonx.lyrics.utils.NeCryptoUtils
import com.lonx.lyrics.utils.YrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random
import androidx.core.content.edit
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.RequestBody

class NeSource(
    private val api: NeApi,
    private val json: Json,
    private val context: Context,
    private val okHttpClient: OkHttpClient
): SearchSource {
    override val sourceType = Source.NE


    // Cookie 管理
    private val cookieMap = mutableMapOf<String, String>()
    private val DEVICEID_XOR_KEY = "3go8&$8*3*3h0k(2)2"


    private val initMutex = Mutex()
    private var isInitialized = false
    private var userId: Long = 0

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    // 模拟 PC 客户端常量
    private val APP_VER = "3.1.3.203419"
    private val OS_VER = "Microsoft-Windows-10--build-19045-64bit"
    private val DEVICE_ID = UUID.randomUUID().toString().replace("-", "")
    private var clientSign: String = ""

    private val PREF_NAME = "ne_source_prefs"
    private val KEY_COOKIES = "cookies"
    private val KEY_USER_ID = "user_id"
    private val KEY_INIT_TIME = "init_time"
    private val EXPIRE_TIME = 10 * 24 * 60 * 60 * 1000L // 登录有效期 10 天

    init {
        // 初始化时生成 clientSign
        clientSign = generateClientSign()
    }
    /**
     * 尝试从本地加载缓存的 Session
     */
    private fun loadSession(): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedInitTime = prefs.getLong(KEY_INIT_TIME, 0L)

        if (System.currentTimeMillis() - savedInitTime > EXPIRE_TIME) return false

        val savedCookiesJson = prefs.getString(KEY_COOKIES, null)
        val savedUserId = prefs.getLong(KEY_USER_ID, 0L)

        return if (!savedCookiesJson.isNullOrEmpty() && savedUserId != 0L) {
            try {
                val map: Map<String, String> = json.decodeFromString(savedCookiesJson)
                cookieMap.clear()
                cookieMap.putAll(map)
                userId = savedUserId
                true
            } catch (e: Exception) { false }
        } else false
    }

    /**
     * 保存 Session 到本地
     */
    private fun saveSession(uid: Long, cookies: Map<String, String>) {
        val prefs = context.getSharedPreferences("ne_source_prefs", Context.MODE_PRIVATE)
        prefs.edit {
            putLong("user_id", uid)
                .putString("cookies", json.encodeToString(cookies))
                .putLong("init_time", System.currentTimeMillis())
        }
    }
    /**
     * 生成 ClientSign
     * 格式: MAC@@@RANDOM@@@@@@HASH
     */
    private fun generateClientSign(): String {
        val mac = (1..6).joinToString(":") {
            "%02X".format(Random.nextInt(256))
        }
        val randomStr = (1..8).map {
            ('A'..'Z').random()
        }.joinToString("")
        val hashPart = (1..64).map {
            "0123456789abcdef".random()
        }.joinToString("")

        return "$mac@@@$randomStr@@@@@@$hashPart"
    }

    private suspend fun ensureInit() {
        if (isInitialized) return

        initMutex.withLock {
            if (isInitialized) return

            if (loadSession()) {
                isInitialized = true
                Log.d("NeSource", "从缓存恢复会话成功, uid: $userId")
                return@withLock
            }

            Log.d("NeSource", "开始执行匿名登录流程...")

            val modes = listOf(
                "MS-iCraft B760M WIFI", "ASUS ROG STRIX Z790", "MSI MAG B550 TOMAHAWK",
                "ASRock X670E Taichi", "GIGABYTE Z790 AORUS ELITE"
            )
            val preCookies = mutableMapOf(
                "os" to "pc",
                "deviceId" to DEVICE_ID,
                "osver" to "Microsoft-Windows-10--build-${Random.nextInt(20000, 30000)}-64bit",
                "clientSign" to clientSign,
                "channel" to "netease",
                "mode" to modes.random(),
                "appver" to APP_VER
            )

            val path = "/eapi/register/anonimous"

            val username = getAnonimousUsername(DEVICE_ID)

            val params = buildJsonObject {
                put("username", username)
                put("e_r", true)
            }

            try {
                val requestBody = buildBody(path, params, preCookies)

                // 使用 OkHttpClient 直接请求以支持多个 cookie 头
                val requestBuilder = Request.Builder()
                    .url("https://interface.music.163.com$path")
                    .post(requestBody)
                    .header("accept", "*/*")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/$APP_VER")
                    .header("mconfig-info", """{"IuRPVVmc3WWul9fT":{"version":733184,"appver":"3.1.3.203419"}}""")
                    .header("origin", "orpheus://orpheus")
                    .header("sec-ch-ua", "\"Chromium\";v=\"91\"")
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-fetch-site", "cross-site")
                    .header("sec-fetch-mode", "cors")
                    .header("sec-fetch-dest", "empty")
                    .header("accept-language", "en-US,en;q=0.9")

                // 添加多个 cookie 头（每个 cookie 单独一行）
                preCookies.forEach { (k, v) ->
                    requestBuilder.addHeader("cookie", "$k=$v")
                }

                val response = okHttpClient.newCall(requestBuilder.build()).execute()
                val responseBodyBytes = response.body?.bytes() ?: byteArrayOf()

                if (response.isSuccessful && responseBodyBytes.isNotEmpty()) {
                    val setCookieHeaders = response.headers.values("Set-Cookie")
                    val responseCookies = mutableMapOf<String, String>()
                    setCookieHeaders.forEach { cookieLine ->
                        val cookiePair = cookieLine.split(";")[0].split("=")
                        if (cookiePair.size >= 2) {
                            responseCookies[cookiePair[0]] = cookiePair[1]
                        }
                    }

                    cookieMap.clear()
                    cookieMap.putAll(preCookies)
                    responseCookies["MUSIC_A"]?.let { cookieMap["MUSIC_A"] = it }
                    responseCookies["NMTID"]?.let { cookieMap["NMTID"] = it }
                    responseCookies["__csrf"]?.let { cookieMap["__csrf"] = it }

                    val wnmcid = "${(1..6).map { ('a'..'z').random() }.joinToString("")}.${System.currentTimeMillis()}.01.0"
                    cookieMap["WNMCID"] = wnmcid

                    val decrypted = NeCryptoUtils.aesDecrypt(responseBodyBytes)

                    if (decrypted.isNotEmpty()) {
                        val jsonRes = json.decodeFromString<JsonObject>(decrypted)
                        val code = jsonRes["code"]?.jsonPrimitive?.content ?: "unknown"

                        if (code == "200") {
                            userId = jsonRes["userId"]?.jsonPrimitive?.longOrNull ?: 0
                            saveSession(userId, cookieMap)
                            isInitialized = true
                            Log.d("NeSource", "匿名登录成功: userId=$userId")
                        } else {
                            Log.e("NeSource", "登录失败, 服务器返回 code: $code")
                        }
                    } else {
                        Log.e("NeSource", "解密失败，响应体可能不是加密数据")
                    }
                } else {
                    Log.e("NeSource", "HTTP 请求失败: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("NeSource", "初始化过程中发生异常", e)
            }
        }
    }

    /**
     * 构造 EAPI 加密后的请求体
     * @param path 接口路径，例如 "/eapi/register/anonimous"
     * @param params 业务参数
     * @param preCookies 用于生成内部 header 字段的设备信息
     */
    private fun buildBody(path: String, params: JsonObject, preCookies: Map<String, String>): RequestBody {
        val headerParam = buildJsonObject {
            put("clientSign", preCookies["clientSign"] ?: "")
            put("os", preCookies["os"] ?: "")
            put("appver", preCookies["appver"] ?: "")
            put("deviceId", preCookies["deviceId"] ?: "")
            put("requestId", 0)
            put("osver", preCookies["osver"] ?: "")
        }

        val finalParamsMap = params.toMutableMap()
        finalParamsMap["header"] = JsonPrimitive(json.encodeToString(headerParam))

        if (!finalParamsMap.containsKey("e_r")) {
            finalParamsMap["e_r"] = JsonPrimitive(true)
        }

        val paramsStr = json.encodeToString(JsonObject(finalParamsMap))
        Log.d("NeSource", "buildBody 最终参数: $paramsStr")

        val encryptPath = path.replace("/eapi/", "/api/")

        val encryptedBytes = NeCryptoUtils.encryptParams(encryptPath, paramsStr)
        val encryptedHexString = encryptedBytes.joinToString("") { "%02x".format(it) }.uppercase()

        val formBody = "params=$encryptedHexString"
        return formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    private suspend fun doRequest(
        path: String,
        params: JsonObject,
        encryptPath: String? = null
    ): String = withContext(Dispatchers.Default) {

        val headerParam = buildJsonObject {
            put("clientSign", clientSign)
            put("os", "pc")
            put("appver", APP_VER)
            put("deviceId", DEVICE_ID)
            put("requestId", 0)
            put("osver", OS_VER)
        }

        val headerParamString = json.encodeToString(headerParam)
        val finalParams = params.toMutableMap()
        finalParams["header"] = JsonPrimitive(headerParamString)
        if (!finalParams.containsKey("e_r")) {
            finalParams["e_r"] = JsonPrimitive(true)
        }

        val mergedParams = JsonObject(finalParams)
        val paramsStr = json.encodeToString(mergedParams)
        val actualEncryptPath = encryptPath ?: path.replace("/eapi/", "/api/")

        // 加密
        val encryptedBytes = NeCryptoUtils.encryptParams(actualEncryptPath, paramsStr)
        val encryptedHexString = encryptedBytes.joinToString("") { "%02x".format(it) }.uppercase()
        val formBody = "params=$encryptedHexString"
        val requestBody = formBody.toRequestBody("application/x-www-form-urlencoded".toMediaType())

        // 使用 OkHttpClient 直接请求以支持多个 cookie 头
        val requestBuilder = Request.Builder()
            .url("https://interface.music.163.com$path")
            .post(requestBody)
            .header("accept", "*/*")
            .header("content-type", "application/x-www-form-urlencoded")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/$APP_VER")
            .header("mconfig-info", """{"IuRPVVmc3WWul9fT":{"version":733184,"appver":"3.1.3.203419"}}""")
            .header("origin", "orpheus://orpheus")
            .header("sec-ch-ua", "\"Chromium\";v=\"91\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-fetch-site", "cross-site")
            .header("sec-fetch-mode", "cors")
            .header("sec-fetch-dest", "empty")
            .header("accept-language", "en-US,en;q=0.9")

        // 添加多个 cookie 头（每个 cookie 单独一行）
        cookieMap.forEach { (k, v) ->
            requestBuilder.addHeader("cookie", "$k=$v")
        }

        try {
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            var responseBytes = response.body?.bytes() ?: return@withContext ""
            if (responseBytes.isEmpty()) return@withContext ""

            // 检查是否是 gzip 压缩数据 (magic number: 1F 8B)
            if (responseBytes.size >= 2 && responseBytes[0] == 0x1F.toByte() && responseBytes[1] == 0x8B.toByte()) {
                responseBytes = decompressGzip(responseBytes)
            }
            // 检查是否是 zlib/deflate 压缩数据 (magic number: 78 9C, 78 DA, 78 01 等)
            else if (responseBytes.size >= 2 && responseBytes[0] == 0x78.toByte()) {
                responseBytes = decompressZlib(responseBytes)
            }

            // 解密
            val decrypted = NeCryptoUtils.aesDecrypt(responseBytes)

            // 检测 Session 是否失效
            if (decrypted.contains("\"code\":301") || decrypted.contains("\"code\":401")) {
                Log.w("NeSource", "Session invalid (code 301/401), clearing cache...")
                isInitialized = false // 触发下一次请求重连
            }

            return@withContext decrypted
        } catch (e: Exception) {
            Log.e("NeSource", "doRequest 请求异常", e)
            return@withContext ""
        }
    }

    override suspend fun search(keyword: String, page: Int, separator: String, pageSize: Int): List<SongSearchResult> = withContext(
        Dispatchers.IO) {
        ensureInit()

        val path = "/eapi/search/song/list/page"
        val offset = (page - 1) * 20

        val params = buildJsonObject {
            put("limit", pageSize.toString())
            put("offset", offset.toString())
            put("keyword", keyword)
            put("scene", "NORMAL")
            put("needCorrect", "true")
        }

        try {
            val rawJson = doRequest(path, params)
            val resp = json.decodeFromString<NeSearchResponse>(rawJson)

            if (resp.code != 200) return@withContext emptyList()
            return@withContext resp.data?.resources?.map { res ->
                val song = res.baseInfo.simpleSongData
                SongSearchResult(
                    id = song.id.toString(),
                    title = song.name,
                    artist = song.artists.joinToString(separator) { it.name },
                    album = song.album.name,
                    duration = song.duration,
                    source = Source.NE,
                    date = song.publishTime?.let { formatMillisToDate(it) } ?: "",
                    trackerNumber = song.trackerNumber,
                    picUrl = song.album.picUrl
                )
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e("NeSource", "Search exception", e)
            return@withContext emptyList()
        }
    }

    override suspend fun getLyrics(song: SongSearchResult): LyricsResult? = withContext(Dispatchers.IO) {
        ensureInit()
        val path = "/eapi/song/lyric/v1"
        val params = buildJsonObject {
            put("id", song.id.toLongOrNull() ?: 0)
            put("lv", "-1")
            put("tv", "-1")
            put("rv", "-1")
            put("yv", "-1")
        }
        val rawJson = doRequest(path, params)
        val resp = try {
            json.decodeFromString<NeLyricResponse>(rawJson)
        } catch (e: Exception) { return@withContext null }
        return@withContext YrcParser.parse(
            yrc = resp.yrc?.lyric,
            lrc = resp.lrc?.lyric,
            tlyric = resp.tlyric?.lyric,
            romalrc = resp.romalrc?.lyric
        )
    }


    /**
     * 生成游客登录所需的 username
     */
    fun getAnonimousUsername(deviceId: String): String {
        val keyLength = DEVICEID_XOR_KEY.length
        val sb = StringBuilder()

        deviceId.forEachIndexed { index, char ->
            val keyChar = DEVICEID_XOR_KEY[index % keyLength]
            val xoredChar = (char.code xor keyChar.code).toChar()
            sb.append(xoredChar)
        }
        val xoredString = sb.toString()
        val md = MessageDigest.getInstance("MD5")
        val md5Digest = md.digest(xoredString.toByteArray(Charsets.UTF_8))

        val base64Md5 = Base64.encodeToString(md5Digest, Base64.NO_WRAP)

        val combinedStr = "$deviceId $base64Md5"

        return Base64.encodeToString(combinedStr.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
    private fun formatMillisToDate(millis: Long): String {
        if (millis <= 0L) return ""

        return Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(formatter)
    }

    /**
     * 解压 gzip 数据
     */
    private fun decompressGzip(compressed: ByteArray): ByteArray {
        return try {
            GZIPInputStream(ByteArrayInputStream(compressed)).use { gzipStream ->
                gzipStream.readBytes()
            }
        } catch (e: Exception) {
            Log.e("NeSource", "Gzip 解压失败", e)
            compressed
        }
    }

    /**
     * 解压 zlib/deflate 数据
     */
    private fun decompressZlib(compressed: ByteArray): ByteArray {
        return try {
            InflaterInputStream(ByteArrayInputStream(compressed)).use { inflaterStream ->
                inflaterStream.readBytes()
            }
        } catch (e: Exception) {
            Log.e("NeSource", "Zlib 解压失败", e)
            compressed
        }
    }
}