package com.prismde

import com.prismde.feature_editor.language.PrismCodeLanguage
import com.prismde.feature_editor.language.PrismLanguageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

class PrismCodeLanguageTest {

    @Test
    fun testLanguageDetection() {
        assertEquals(PrismLanguageType.CPP, PrismCodeLanguage.forFile(File("main.cpp")).type)
        assertEquals(PrismLanguageType.CPP, PrismCodeLanguage.forFile(File("native-lib.cpp")).type)
        assertEquals(PrismLanguageType.CPP, PrismCodeLanguage.forFile(File("header.hpp")).type)
        assertEquals(PrismLanguageType.CPP, PrismCodeLanguage.forFile(File("test.c")).type)
        assertEquals(PrismLanguageType.CMAKE, PrismCodeLanguage.forFile(File("CMakeLists.txt")).type)
        assertEquals(PrismLanguageType.CMAKE, PrismCodeLanguage.forFile(File("toolchain.cmake")).type)
        assertEquals(PrismLanguageType.MAKEFILE, PrismCodeLanguage.forFile(File("Android.mk")).type)
        assertEquals(PrismLanguageType.MAKEFILE, PrismCodeLanguage.forFile(File("Application.mk")).type)
        assertEquals(PrismLanguageType.MAKEFILE, PrismCodeLanguage.forFile(File("Makefile")).type)
        assertEquals(PrismLanguageType.JAVA, PrismCodeLanguage.forFile(File("MainActivity.java")).type)
        assertEquals(PrismLanguageType.JAVA, PrismCodeLanguage.forFile(File("App.kt")).type)
        assertEquals(PrismLanguageType.JSON, PrismCodeLanguage.forFile(File("config.json")).type)
    }

    @Test
    fun testSymbolPairs() {
        val lang = PrismCodeLanguage(PrismLanguageType.CPP)
        val symbolPairs = lang.symbolPairs
        assertNotNull("Should match open paren", symbolPairs.matchBestPairBySingleChar('('))
        assertNotNull("Should match open brace", symbolPairs.matchBestPairBySingleChar('{'))
        assertNotNull("Should match open bracket", symbolPairs.matchBestPairBySingleChar('['))
        assertNotNull("Should match double quote", symbolPairs.matchBestPairBySingleChar('"'))
    }
}
