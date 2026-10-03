package com.prismde
 
import com.prismde.feature_build.engine.AiAgentEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
 
class AiAgentEngineTest {
 
     private val engine = AiAgentEngine()
 
     @Test
     fun testExtractStandardXmlToolCall() {
         val text = """
             I need to check the file.
             <tool_call name="read_file">
               <path>jni/main.cpp</path>
             </tool_call>
         """.trimIndent()
 
         val calls = engine.extractToolCalls(text)
         assertEquals(1, calls.size)
         assertEquals("read_file", calls[0].name)
         assertEquals("jni/main.cpp", calls[0].args["path"])
     }
 
     @Test
     fun testExtractDeepSeekDsmlToolCalls() {
         // Exact text from user's screenshot
         val text = """
             Теперь посмотрю на style.h и style.cpp, чтобы понять доступные стили для консистентного оформления табов.
 
             <tool_call name="read_file">
             < |  | DSML |  | parameter name="path">jni/ui/style.h</ |  | DSML |  | parameter>
             </ |  | DSML |  | invoke>
             < |  | DSML |  | invoke name="read_file">
             < |  | DSML |  | parameter name="path">jni/ui/style.cpp</ |  | DSML |  | parameter>
             </ |  | DSML |  | invoke>
             </ |  | DSML |  | calls>
         """.trimIndent()
 
         val calls = engine.extractToolCalls(text)
         assertEquals(2, calls.size)
 
         assertEquals("read_file", calls[0].name)
         assertEquals("jni/ui/style.h", calls[0].args["path"])
 
         assertEquals("read_file", calls[1].name)
         assertEquals("jni/ui/style.cpp", calls[1].args["path"])
     }
 
     @Test
     fun testExtractWriteFileToolCall() {
         val text = """
             <tool_call name="write_file">
               <path>jni/test.cpp</path>
               <content>#include <iostream>
             int main() { return 0; }
             </content>
             </tool_call>
         """.trimIndent()
 
         val calls = engine.extractToolCalls(text)
         assertEquals(1, calls.size)
         assertEquals("write_file", calls[0].name)
         assertEquals("jni/test.cpp", calls[0].args["path"])
         assertTrue(calls[0].args["content"]?.contains("main()") == true)
     }
 
     @Test
     fun testCleanResponseTextRemovesDsmlAndToolCalls() {
         val text = """
             Теперь посмотрю на style.h и style.cpp, чтобы понять доступные стили для консистентного оформления табов.
 
             <tool_call name="read_file">
             < |  | DSML |  | parameter name="path">jni/ui/style.h</ |  | DSML |  | parameter>
             </ |  | DSML |  | invoke>
             < |  | DSML |  | invoke name="read_file">
             < |  | DSML |  | parameter name="path">jni/ui/style.cpp</ |  | DSML |  | parameter>
             </ |  | DSML |  | invoke>
             </ |  | DSML |  | calls>
         """.trimIndent()
 
         val cleaned = engine.cleanResponseText(text)
         assertEquals("Теперь посмотрю на style.h и style.cpp, чтобы понять доступные стили для консистентного оформления табов.", cleaned)
     }
}
