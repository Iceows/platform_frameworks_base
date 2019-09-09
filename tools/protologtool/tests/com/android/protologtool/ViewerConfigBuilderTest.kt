/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.protologtool

import com.android.json.stream.JsonReader
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.MethodCallExpr
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.io.StringReader

class ViewerConfigBuilderTest {
    companion object {
        private val TAG1 = "WM_TEST"
        private val TAG2 = "WM_DEBUG"
        private val TEST1 = ViewerConfigParser.ConfigEntry("test1", LogLevel.INFO.name, TAG1)
        private val TEST2 = ViewerConfigParser.ConfigEntry("test2", LogLevel.DEBUG.name, TAG2)
        private val TEST3 = ViewerConfigParser.ConfigEntry("test3", LogLevel.ERROR.name, TAG2)
    }

    private val processor: ProtoLogCallProcessor = Mockito.mock(ProtoLogCallProcessor::class.java)
    private val configBuilder = ViewerConfigBuilder(processor)
    private val dummyCompilationUnit = CompilationUnit()

    private fun <T> any(type: Class<T>): T = Mockito.any<T>(type)

    private fun parseConfig(json: String): Map<Int, ViewerConfigParser.ConfigEntry> {
        return ViewerConfigParser().parseConfig(JsonReader(StringReader(json)))
    }

    @Test
    fun processClass() {
        Mockito.`when`(processor.process(any(CompilationUnit::class.java),
                any(ProtoLogCallVisitor::class.java))).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(MethodCallExpr(), TEST1.messageString, LogLevel.INFO,
                    LogGroup("TEST_GROUP", true, true, TAG1))
            visitor.processCall(MethodCallExpr(), TEST2.messageString, LogLevel.DEBUG,
                    LogGroup("DEBUG_GROUP", true, true, TAG2))
            visitor.processCall(MethodCallExpr(), TEST3.messageString, LogLevel.ERROR,
                    LogGroup("DEBUG_GROUP", true, true, TAG2))

            invocation.arguments[0] as CompilationUnit
        }

        configBuilder.processClass(dummyCompilationUnit)

        val parsedConfig = parseConfig(configBuilder.build())
        assertEquals(3, parsedConfig.size)
        assertEquals(TEST1, parsedConfig[CodeUtils.hash(TEST1.messageString,
                LogLevel.INFO)])
        assertEquals(TEST2, parsedConfig[CodeUtils.hash(TEST2.messageString,
                LogLevel.DEBUG)])
        assertEquals(TEST3, parsedConfig[CodeUtils.hash(TEST3.messageString,
                LogLevel.ERROR)])
    }

    @Test
    fun processClass_nonUnique() {
        Mockito.`when`(processor.process(any(CompilationUnit::class.java),
                any(ProtoLogCallVisitor::class.java))).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(MethodCallExpr(), TEST1.messageString, LogLevel.INFO,
                    LogGroup("TEST_GROUP", true, true, TAG1))
            visitor.processCall(MethodCallExpr(), TEST1.messageString, LogLevel.INFO,
                    LogGroup("TEST_GROUP", true, true, TAG1))
            visitor.processCall(MethodCallExpr(), TEST1.messageString, LogLevel.INFO,
                    LogGroup("TEST_GROUP", true, true, TAG1))

            invocation.arguments[0] as CompilationUnit
        }

        configBuilder.processClass(dummyCompilationUnit)

        val parsedConfig = parseConfig(configBuilder.build())
        assertEquals(1, parsedConfig.size)
        assertEquals(TEST1, parsedConfig[CodeUtils.hash(TEST1.messageString, LogLevel.INFO)])
    }

    @Test
    fun processClass_disabled() {
        Mockito.`when`(processor.process(any(CompilationUnit::class.java),
                any(ProtoLogCallVisitor::class.java))).thenAnswer { invocation ->
            val visitor = invocation.arguments[1] as ProtoLogCallVisitor

            visitor.processCall(MethodCallExpr(), TEST1.messageString, LogLevel.INFO,
                    LogGroup("TEST_GROUP", true, true, TAG1))
            visitor.processCall(MethodCallExpr(), TEST2.messageString, LogLevel.DEBUG,
                    LogGroup("DEBUG_GROUP", false, true, TAG2))
            visitor.processCall(MethodCallExpr(), TEST3.messageString, LogLevel.ERROR,
                    LogGroup("DEBUG_GROUP", true, false, TAG2))

            invocation.arguments[0] as CompilationUnit
        }

        configBuilder.processClass(dummyCompilationUnit)

        val parsedConfig = parseConfig(configBuilder.build())
        assertEquals(2, parsedConfig.size)
        assertEquals(TEST1, parsedConfig[CodeUtils.hash(TEST1.messageString, LogLevel.INFO)])
        assertEquals(TEST3, parsedConfig[CodeUtils.hash(TEST3.messageString, LogLevel.ERROR)])
    }
}
