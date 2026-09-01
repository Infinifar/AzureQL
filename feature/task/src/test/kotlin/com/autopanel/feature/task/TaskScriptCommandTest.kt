package com.autopanel.feature.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskScriptCommandTest {
    @Test
    fun `resolves common QingLong script commands`() {
        assertEquals("foo.py", resolveTaskScriptPath("task foo.py"))
        assertEquals("foo.py", resolveTaskScriptPath("python /ql/scripts/foo.py --quiet"))
        assertEquals("jobs/daily.js", resolveTaskScriptPath("node jobs/daily.js --today"))
        assertEquals(
            "repo folder/daily task.py",
            resolveTaskScriptPath("python3 '/ql/data/scripts/repo folder/daily task.py' --once")
        )
    }

    @Test
    fun `rejects ambiguous or compound commands`() {
        assertNull(resolveTaskScriptPath("python first.py second.py"))
        assertNull(resolveTaskScriptPath("task first.py && task second.py"))
        assertNull(resolveTaskScriptPath("echo foo.py"))
        assertNull(resolveTaskScriptPath("python ../outside.py"))
        assertNull(resolveTaskScriptPath("python /tmp/outside.py"))
        assertNull(resolveTaskScriptPath("python C:\\outside.py"))
    }
}
