package org.xdty.config

import com.google.gson.Gson
import java.io.InputStreamReader


class Config private constructor(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    region: String,
    private var bucket: String
) {

    private var minIo: MinIo = MinIo.Builder()
        .endpoint(endpoint)
        .accessKey(accessKey)
        .secretKey(secretKey)
        .region(region)
        .build()

    fun <T> get(classOfT: Class<T>, objectName: String): T {
        val reader = InputStreamReader(minIo.get(bucket, objectName), "UTF-8")
        return Gson().fromJson(reader, classOfT)
    }

    fun getString(objectName: String): String {
        return minIo.get(bucket, objectName).bufferedReader().use { it.readText() }
    }

    data class Builder(
        private var endpoint: String = "",
        private var accessKey: String = "",
        private var secretKey: String = "",
        private var region: String = "cn",
        private var bucket: String = "config"
    ) {
        fun endpoint(endpoint: String) = apply { this.endpoint = endpoint }
        fun accessKey(accessKey: String) = apply { this.accessKey = accessKey }
        fun secretKey(secretKey: String) = apply { this.secretKey = secretKey }
        fun region(region: String) = apply { this.region = region }
        fun bucket(bucket: String) = apply { this.bucket = bucket }
        fun build() = Config(endpoint, accessKey, secretKey, region, bucket)
    }
}
