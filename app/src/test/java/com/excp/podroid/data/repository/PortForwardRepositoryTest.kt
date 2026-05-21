/*
 * Podroid - Rootless Podman for Android
 * Copyright (C) 2024-2026 Podroid contributors
 *
 * Unit tests for PortForwardRule pure logic and dedup helper.
 */
package com.excp.podroid.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Bug 4: PortForwardRule.deserialize must validate protocol and port range.
 * Bug 5: addRule must dedup on (hostPort, protocol) key.
 */
class PortForwardRepositoryTest {

    // ------------------------------------------------------------------
    // Bug 4 — deserialize validation
    // ------------------------------------------------------------------

    @Test
    fun `deserialize valid tcp rule round-trips`() {
        val rule = PortForwardRule(8080, 80, "tcp")
        val result = PortForwardRule.deserialize(rule.serialize())
        assertNotNull(result)
        assertEquals(rule, result)
    }

    @Test
    fun `deserialize valid udp rule round-trips`() {
        val rule = PortForwardRule(9922, 22, "udp")
        val result = PortForwardRule.deserialize(rule.serialize())
        assertNotNull(result)
        assertEquals(rule, result)
    }

    @Test
    fun `deserialize rejects unknown protocol`() {
        assertNull(PortForwardRule.deserialize("sctp:8080:80"))
    }

    @Test
    fun `deserialize rejects port zero`() {
        assertNull(PortForwardRule.deserialize("tcp:0:80"))
    }

    @Test
    fun `deserialize rejects guest port zero`() {
        assertNull(PortForwardRule.deserialize("tcp:8080:0"))
    }

    @Test
    fun `deserialize rejects port above 65535`() {
        assertNull(PortForwardRule.deserialize("tcp:65536:80"))
    }

    @Test
    fun `deserialize rejects guest port above 65535`() {
        assertNull(PortForwardRule.deserialize("tcp:8080:65536"))
    }

    @Test
    fun `deserialize rejects negative port`() {
        assertNull(PortForwardRule.deserialize("tcp:-1:80"))
    }

    @Test
    fun `deserialize allows boundary port 1`() {
        assertNotNull(PortForwardRule.deserialize("tcp:1:1"))
    }

    @Test
    fun `deserialize allows boundary port 65535`() {
        assertNotNull(PortForwardRule.deserialize("tcp:65535:65535"))
    }

    @Test
    fun `deserialize rejects malformed string with wrong part count`() {
        assertNull(PortForwardRule.deserialize("tcp:8080"))
        assertNull(PortForwardRule.deserialize("tcp:8080:80:extra"))
    }

    // ------------------------------------------------------------------
    // Bug 5 — (hostPort, protocol) dedup helper
    // ------------------------------------------------------------------

    @Test
    fun `deduplicateByKey removes existing entry with same hostPort and protocol`() {
        val existing = setOf(
            PortForwardRule(8080, 80, "tcp").serialize(),
            PortForwardRule(9922, 22, "tcp").serialize(),
        )
        val newRule = PortForwardRule(8080, 443, "tcp")
        val result = deduplicatePortForwards(existing, newRule)
        // Old tcp:8080:80 gone; new tcp:8080:443 present; 9922 untouched.
        assertEquals(2, result.size)
        assert(newRule.serialize() in result) { "new rule should be in result" }
        assert(PortForwardRule(9922, 22, "tcp").serialize() in result) { "unrelated rule should remain" }
        assert(PortForwardRule(8080, 80, "tcp").serialize() !in result) { "old rule should be removed" }
    }

    @Test
    fun `deduplicateByKey keeps different protocol on same host port`() {
        val existing = setOf(
            PortForwardRule(8080, 80, "tcp").serialize(),
        )
        val newRule = PortForwardRule(8080, 80, "udp")
        val result = deduplicatePortForwards(existing, newRule)
        assertEquals(2, result.size)
        assert(PortForwardRule(8080, 80, "tcp").serialize() in result)
        assert(newRule.serialize() in result)
    }

    @Test
    fun `deduplicateByKey adds new rule when no conflict`() {
        val existing = setOf(
            PortForwardRule(9922, 22, "tcp").serialize(),
        )
        val newRule = PortForwardRule(8080, 80, "tcp")
        val result = deduplicatePortForwards(existing, newRule)
        assertEquals(2, result.size)
    }

    @Test
    fun `deduplicateByKey handles empty set`() {
        val newRule = PortForwardRule(8080, 80, "tcp")
        val result = deduplicatePortForwards(emptySet<String>(), newRule)
        assertEquals(setOf(newRule.serialize()), result)
    }
}
