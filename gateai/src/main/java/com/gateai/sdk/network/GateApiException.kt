package com.gateai.sdk.network

class GateApiException(
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String?
) : Exception("HTTP $statusCode")

