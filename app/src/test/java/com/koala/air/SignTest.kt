package com.koala.air

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

class SignTest {
    @Test
    fun canonicalMatchesPythonLibrary() {
        val d = ConnectLife.signedRequest(
            "TOK",
            JSONObject().put("puid", "P1").put("properties", JSONObject().put("t_power", "1")),
            now = 1700000000000, rand = "abc123",
        )
        // Chaîne produite par la bibliothèque Python connectlife 0.15.0 pour les mêmes entrées
        val expected = "accessToken=TOK&appId=47110565134383&appSecret=yOzhz6junYno-nmULM3Wr7PU_dpSZN22ZdluvVWZ4uW5ZwwG8fIGCHTbrhcnU-iv" +
            "&languageId=12&properties={\"t_power\":\"1\"}&puid=P1&randStr=abc123&timeStamp=1700000000000&timezone=1.0&version=5.0"
        assertEquals(expected, ConnectLife.canonical(d))
        // Signature RSA 2048 bits : 256 octets
        assertEquals(256, Base64.getDecoder().decode(d.getString("sign")).size)
    }

    @Test
    fun compactJsonMatchesPython() {
        assertEquals("{\"a\":\"\\u00e9/x\\\"\"}", ConnectLife.compactJson(JSONObject().put("a", "é/x\"")))
    }
}
