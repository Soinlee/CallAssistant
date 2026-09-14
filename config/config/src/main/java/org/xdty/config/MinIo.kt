package org.xdty.config

import io.minio.MinioClient
import io.minio.errors.InvalidEndpointException
import io.minio.errors.InvalidPortException
import java.io.InputStream

class MinIo private constructor(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    region: String
) {

    private lateinit var client: MinioClient

    init {
        try {
            client = MinioClient(endpoint, accessKey, secretKey, region)
        } catch (e: InvalidEndpointException) {
            e.printStackTrace()
        } catch (e: InvalidPortException) {
            e.printStackTrace()
        }
    }

    fun get(bucket: String, objectName: String): InputStream {
        return client.getObject(bucket, objectName)
    }


    data class Builder(
        private var endpoint: String = "",
        private var accessKey: String = "",
        private var secretKey: String = "",
        private var region: String = ""

    ) {
        fun endpoint(endpoint: String) = apply { this.endpoint = endpoint }
        fun accessKey(accessKey: String) = apply { this.accessKey = accessKey }
        fun secretKey(secretKey: String) = apply { this.secretKey = secretKey }
        fun region(region: String) = apply { this.region = region }
        fun build() = MinIo(endpoint, accessKey, secretKey, region)
    }
}