package com.github.chsxthwik.gochat

import com.github.chsxthwik.gochat.data.ApiProtocol
import com.github.chsxthwik.gochat.data.GoApi
import com.github.chsxthwik.gochat.data.GoError
import com.github.chsxthwik.gochat.data.WireMessage
import com.github.chsxthwik.gochat.data.requestBody
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoApiTest {

    private val api = GoApi()

    private fun errBody(msg: String) = """{"error":{"message":"$msg"}}"""

    @Test
    fun `errorFor maps http codes to kinds`() {
        assertEquals(GoError.Kind.INVALID_KEY, api.errorFor(401, "").kind)
        assertEquals(GoError.Kind.INVALID_KEY, api.errorFor(403, errBody("bad token")).kind)
        assertEquals(GoError.Kind.QUOTA, api.errorFor(402, "").kind)
        assertEquals(GoError.Kind.RATE_LIMITED, api.errorFor(429, "").kind)
        assertEquals(GoError.Kind.SERVER, api.errorFor(503, "").kind)
        assertEquals(GoError.Kind.UNKNOWN, api.errorFor(418, "").kind)
    }

    @Test
    fun `errorFor detects quota wording inside 401`() {
        assertEquals(GoError.Kind.QUOTA, api.errorFor(401, errBody("weekly quota exceeded")).kind)
        assertEquals(GoError.Kind.QUOTA, api.errorFor(403, errBody("rate limit tier")).kind)
    }

    @Test
    fun `errorFor extracts and truncates detail`() {
        val long = "x".repeat(300)
        val e = api.errorFor(500, errBody(long))
        assertEquals(160, e.detail.length)
        assertEquals("hello", api.errorFor(400, errBody("hello")).detail)
        assertEquals("", api.errorFor(400, "not json").detail)
    }

    @Test
    fun `friendly falls back to detail for unknown`() {
        assertEquals("boom", GoError(GoError.Kind.UNKNOWN, 400, "boom").friendly())
        assertEquals("Something went wrong", GoError(GoError.Kind.UNKNOWN).friendly())
    }

    @Test
    fun `chat completions body carries system as first message and image parts`() {
        val body = api.requestBody(
            ApiProtocol.CHAT_COMPLETIONS, "m1",
            listOf(WireMessage("user", "hi", listOf("data:image/png;base64,AAAA"))),
            "be terse", 0.5f,
        )
        val msgs = body["messages"]!!.jsonArray
        assertEquals("system", msgs[0].jsonObject["role"]!!.jsonPrimitive.content)
        val content = msgs[1].jsonObject["content"]!!.jsonArray
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("image_url", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(body.containsKey("stream_options"))
    }

    @Test
    fun `responses body uses instructions and output_text for assistant`() {
        val body = api.requestBody(
            ApiProtocol.RESPONSES, "m1",
            listOf(WireMessage("user", "q"), WireMessage("assistant", "a")),
            "be terse", 0.5f,
        )
        assertEquals("be terse", body["instructions"]!!.jsonPrimitive.content)
        val input = body["input"]!!.jsonArray
        assertEquals(
            "output_text",
            input[1].jsonObject["content"]!!.jsonArray[0].jsonObject["type"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `messages body splits base64 source and drops system messages`() {
        val body = api.requestBody(
            ApiProtocol.MESSAGES, "m1",
            listOf(
                WireMessage("system", "leak me not"),
                WireMessage("user", "look", listOf("data:image/jpeg;base64,BBBB")),
            ),
            "be terse", 0.5f,
        )
        val msgs = body["messages"]!!.jsonArray
        assertEquals(1, msgs.size) // system role is never echoed inside messages
        val src = msgs[0].jsonObject["content"]!!.jsonArray[1].jsonObject["source"]!!.jsonObject
        assertEquals("image/jpeg", src["media_type"]!!.jsonPrimitive.content)
        assertEquals("BBBB", src["data"]!!.jsonPrimitive.content)
    }
}
