package com.autopanel.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DependencyTypeTest {

    @Test
    fun apiTypesUseNumericCodesExpectedByQinglong() {
        assertEquals(0, DependencyType.toCode(DependencyType.NODEJS))
        assertEquals(1, DependencyType.toCode(DependencyType.PYTHON))
        assertEquals(2, DependencyType.toCode(DependencyType.LINUX))
    }
}
