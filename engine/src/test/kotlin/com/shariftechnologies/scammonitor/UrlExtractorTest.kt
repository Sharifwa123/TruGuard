package com.shariftechnologies.scammonitor

import com.shariftechnologies.scammonitor.domain.reputation.UrlExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlExtractorTest {
    @Test fun `extracts scheme www and bare domains and trims punctuation`() {
        val hosts = UrlExtractor.extract("Go to HTTPS://Example.COM/path?x=1. Or www.test-site.org, or bit.ly/abc!").map { it.host }
        assertEquals(listOf("example.com", "www.test-site.org", "bit.ly"), hosts)
    }

    @Test fun `does not treat file names or abbreviations as hosts`() {
        assertTrue(UrlExtractor.extract("see report.pdf and notes.txt, e.g. this").isEmpty())
    }

    @Test fun `strips port and detects userinfo trick`() {
        val u = UrlExtractor.parse("https://mtn.com.gh@evil.example:8080/x")!!
        assertEquals("evil.example", u.host)
        assertTrue(u.hadUserInfo)
    }

    @Test fun `converts international hosts to punycode`() {
        val u = UrlExtractor.parse("https://münchen.example/")!!
        assertEquals("xn--mnchen-3ya.example", u.host)
        assertTrue(u.wasInternational)
    }

    @Test fun `rejects malformed hosts`() {
        for (s in listOf("http://", "https://..", "http://-bad-.example", "http://a b.example", "http://[::1", "nohost")) assertNull(s, UrlExtractor.parse(s))
    }

    @Test fun `ip addresses are recognised`() {
        assertTrue(UrlExtractor.isIpAddress("192.168.1.1"))
        assertTrue(UrlExtractor.isIpAddress("[::1]"))
        assertTrue(!UrlExtractor.isIpAddress("example.com"))
    }

    @Test fun `caps number of urls`() {
        val text = (1..50).joinToString(" ") { "https://site$it.example" }
        assertEquals(10, UrlExtractor.extract(text).size)
    }
}
