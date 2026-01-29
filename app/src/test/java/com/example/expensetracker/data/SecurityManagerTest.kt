package com.example.expensetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.IllegalArgumentException

class SecurityManagerTest {

    @Test
    fun encryptAndDecrypt_withCorrectPassword_success() {
        val originalData = "{\"key\": \"value\", \"number\": 123}"
        val password = "StrongPassword123!"

        val encryptedJson = SecurityManager.encryptData(originalData, password)
        
        // Assert it's encrypted (looks like JSON but content is hidden)
        assertTrue(SecurityManager.isEncrypted(encryptedJson))
        assertFalse(encryptedJson.contains("value")) 

        val decryptedData = SecurityManager.decryptData(encryptedJson, password)
        assertEquals(originalData, decryptedData)
    }

    @Test
    fun decrypt_withWrongPassword_fails() {
        val originalData = "Secret Data"
        val password = "Password"
        val wrongPassword = "WrongPassword"

        val encryptedJson = SecurityManager.encryptData(originalData, password)

        // AEADBadTagException usually, wrapped or direct
        assertThrows(Exception::class.java) {
            SecurityManager.decryptData(encryptedJson, wrongPassword)
        }
    }
    
    @Test
    fun isEncrypted_validPayload_returnsTrue() {
        val payload = """
            {
                "version": 1,
                "salt": "c2FsdA==",
                "iv": "aXY=",
                "data": "ZGF0YQ=="
            }
        """.trimIndent()
        assertTrue(SecurityManager.isEncrypted(payload))
    }

    @Test
    fun isEncrypted_invalidJson_returnsFalse() {
        assertFalse(SecurityManager.isEncrypted("Not JSON"))
        assertFalse(SecurityManager.isEncrypted("{}"))
    }
    
    @Test
    fun decrypt_invalidFormat_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            SecurityManager.decryptData("Invalid JSON", "pass")
        }
    }
}
