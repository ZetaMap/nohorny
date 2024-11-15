/*
 * This file is part of NoHorny. The plugin securing your server against nsfw builds.
 *
 * MIT License
 *
 * Copyright (c) 2024 Xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.nohorny.image.analyzer

import com.xpdustry.nohorny.extension.toCompletableFuture
import com.xpdustry.nohorny.extension.toJpgByteArray
import com.xpdustry.nohorny.extension.toJsonObject
import com.xpdustry.nohorny.image.NoHornyInformation
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.IOException
import java.util.concurrent.CompletableFuture

internal class SightEngineAnalyzer(
    private val config: AnalyzerConfig.SightEngine,
    private val http: OkHttpClient,
) : ImageAnalyzer {
    override fun analyse(image: BufferedImage): CompletableFuture<NoHornyInformation> {
        val request =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_user", config.sightEngineUser)
                .addFormDataPart("api_secret", config.sightEngineSecret.value)
                .addFormDataPart(
                    "models",
                    config.kinds.joinToString(",") {
                        when (it) {
                            NoHornyInformation.Kind.NUDITY -> "nudity-2.0"
                            NoHornyInformation.Kind.GORE -> "gore"
                        }
                    },
                )
                .addFormDataPart(
                    "media",
                    "image.jpg",
                    image.toJpgByteArray().toRequestBody("image/jpeg".toMediaTypeOrNull(), 0),
                )
                .build()

        return http
            .newCall(
                Request.Builder()
                    .url("https://api.sightengine.com/1.0/check.json")
                    .post(request)
                    .build(),
            )
            .toCompletableFuture()
            .thenApply(Response::toJsonObject)
            .thenCompose { json ->
                LOGGER.debug("SightEngine response: {}", json)

                if (json["status"]!!.jsonPrimitive.content != "success") {
                    return@thenCompose CompletableFuture.failedFuture(
                        IOException("SightEngine API returned error: ${json["error"]}"),
                    )
                }

                val results = mutableMapOf<NoHornyInformation.Kind, Float>()

                if (NoHornyInformation.Kind.NUDITY in config.kinds) {
                    val percent =
                        EXPLICIT_NUDITY_FIELDS.maxOf {
                            json["nudity"]!!.jsonObject[it]!!.jsonPrimitive.float
                        }
                    results[NoHornyInformation.Kind.NUDITY] = percent
                }

                if (NoHornyInformation.Kind.GORE in config.kinds) {
                    val percent = json["gore"]!!.jsonObject["prob"]!!.jsonPrimitive.float
                    results[NoHornyInformation.Kind.GORE] = percent
                }

                val result = results.maxOfOrNull { it.value } ?: 0F
                val rating =
                    when {
                        result > config.unsafeThreshold -> NoHornyInformation.Rating.UNSAFE
                        result > config.warningThreshold -> NoHornyInformation.Rating.WARNING
                        else -> NoHornyInformation.Rating.SAFE
                    }

                return@thenCompose CompletableFuture.completedFuture(
                    NoHornyInformation(rating, results),
                )
            }
    }

    companion object {
        private val LOGGER = LoggerFactory.getLogger(SightEngineAnalyzer::class.java)
        private val EXPLICIT_NUDITY_FIELDS =
            listOf("sexual_activity", "sexual_display", "sextoy", "erotica")
    }
}
