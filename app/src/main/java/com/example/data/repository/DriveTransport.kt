package com.example.data.repository

import com.example.data.model.DriveLedgerDocument
import com.example.data.model.DriveUserIndex
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DriveFileMeta(
    val id: String,
    val name: String,
    val modifiedTime: String = "",
    val appProperties: Map<String, String> = emptyMap()
)

class DriveUnauthorizedException(message: String) : IOException(message)

class DriveApiException(val code: Int, message: String) : IOException(message)

interface DriveTransport {
    suspend fun listFiles(
        accessToken: String,
        query: String,
        spaces: String
    ): List<DriveFileMeta>

    suspend fun getFileMeta(
        accessToken: String,
        fileId: String
    ): DriveFileMeta?

    suspend fun downloadFile(
        accessToken: String,
        fileId: String
    ): String

    suspend fun createFolder(
        accessToken: String,
        name: String
    ): DriveFileMeta

    suspend fun createJsonFile(
        accessToken: String,
        name: String,
        parentId: String,
        jsonContent: String,
        appProperties: Map<String, String>
    ): DriveFileMeta

    suspend fun updateJsonFile(
        accessToken: String,
        fileId: String,
        jsonContent: String
    ): DriveFileMeta
}

object DriveJsonCodec {
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val userIndexAdapter = moshi.adapter(DriveUserIndex::class.java)
    private val ledgerDocAdapter = moshi.adapter(DriveLedgerDocument::class.java)

    fun encodeUserIndex(index: DriveUserIndex): String = userIndexAdapter.toJson(index)
    fun decodeUserIndex(json: String): DriveUserIndex? = runCatching { userIndexAdapter.fromJson(json) }.getOrNull()

    fun encodeLedgerDocument(doc: DriveLedgerDocument): String = ledgerDocAdapter.toJson(doc)
    fun decodeLedgerDocument(json: String): DriveLedgerDocument? = runCatching { ledgerDocAdapter.fromJson(json) }.getOrNull()
}

class OkHttpDriveTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : DriveTransport {

    companion object {
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private val JSON_MEDIA_TYPE = "application/json; charset=UTF-8".toMediaType()
    }

    override suspend fun listFiles(
        accessToken: String,
        query: String,
        spaces: String
    ): List<DriveFileMeta> = withContext(Dispatchers.IO) {
        val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("spaces", spaces)
            .addQueryParameter("q", query)
            .addQueryParameter("fields", "files(id,name,modifiedTime,appProperties)")
            .addQueryParameter("pageSize", "100")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val body = executeForString(request)
        val root = JSONObject(body)
        val arr = root.optJSONArray("files") ?: return@withContext emptyList()
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(parseFileMeta(obj))
            }
        }
    }

    override suspend fun getFileMeta(
        accessToken: String,
        fileId: String
    ): DriveFileMeta? = withContext(Dispatchers.IO) {
        val url = "$DRIVE_FILES_URL/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,name,modifiedTime,appProperties,trashed")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        val body = executeForString(request)
        val obj = JSONObject(body)
        if (obj.optBoolean("trashed", false)) return@withContext null
        parseFileMeta(obj)
    }

    override suspend fun downloadFile(
        accessToken: String,
        fileId: String
    ): String = withContext(Dispatchers.IO) {
        val url = "$DRIVE_FILES_URL/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        executeForString(request)
    }

    override suspend fun createFolder(
        accessToken: String,
        name: String
    ): DriveFileMeta = withContext(Dispatchers.IO) {
        val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("fields", "id,name,modifiedTime,appProperties")
            .build()
        val metaJson = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
        }.toString()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(metaJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        parseFileMeta(JSONObject(executeForString(request)))
    }

    override suspend fun createJsonFile(
        accessToken: String,
        name: String,
        parentId: String,
        jsonContent: String,
        appProperties: Map<String, String>
    ): DriveFileMeta = withContext(Dispatchers.IO) {
        val boundary = "kinledger_${UUID.randomUUID().toString().replace("-", "")}"
        val metadata = JSONObject().apply {
            put("name", name)
            put("mimeType", "application/json")
            put("parents", org.json.JSONArray(listOf(parentId)))
            if (appProperties.isNotEmpty()) {
                val props = JSONObject()
                appProperties.forEach { (k, v) -> props.put(k, v) }
                put("appProperties", props)
            }
        }.toString()

        val multipartBody = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(jsonContent).append("\r\n")
            append("--").append(boundary).append("--")
        }

        val url = DRIVE_UPLOAD_URL.toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id,name,modifiedTime,appProperties")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .post(multipartBody.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        parseFileMeta(JSONObject(executeForString(request)))
    }

    override suspend fun updateJsonFile(
        accessToken: String,
        fileId: String,
        jsonContent: String
    ): DriveFileMeta = withContext(Dispatchers.IO) {
        val url = "$DRIVE_UPLOAD_URL/$fileId".toHttpUrl().newBuilder()
            .addQueryParameter("uploadType", "media")
            .addQueryParameter("fields", "id,name,modifiedTime,appProperties")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .patch(jsonContent.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        parseFileMeta(JSONObject(executeForString(request)))
    }

    private fun parseFileMeta(obj: JSONObject): DriveFileMeta {
        val propsObj = obj.optJSONObject("appProperties")
        val props = mutableMapOf<String, String>()
        if (propsObj != null) {
            val keys = propsObj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                props[k] = propsObj.optString(k)
            }
        }
        return DriveFileMeta(
            id = obj.getString("id"),
            name = obj.optString("name", ""),
            modifiedTime = obj.optString("modifiedTime", ""),
            appProperties = props
        )
    }

    private fun executeForString(request: Request): String {
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (response.isSuccessful) return raw
            if (response.code == 401) {
                throw DriveUnauthorizedException("Google Drive authorization expired (401)")
            }
            val parsedError = runCatching {
                JSONObject(raw).optJSONObject("error")?.optString("message")
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: raw.take(200)
            throw DriveApiException(response.code, parsedError.ifBlank { "Drive API error (${response.code})" })
        }
    }
}
