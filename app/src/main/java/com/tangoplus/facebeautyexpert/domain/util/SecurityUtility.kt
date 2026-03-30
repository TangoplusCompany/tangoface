package com.tangoplus.facebeautyexpert.domain.util

import java.security.MessageDigest

object SecurityUtility {


//    fun encryptAndSaveToExternal(
//        context: Context,
//        uri: Uri,
//        data: ByteArray,
//        secretKey: String
//    ) {
//        val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
//        val cipher = Cipher.getInstance("AES")
//        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
//
//        val encryptedBytes = cipher.doFinal(data)
//
//        context.contentResolver.openOutputStream(uri)?.use { output ->
//            output.write(encryptedBytes)
//        }
//    }
//
//    fun decryptFromUri(context: Context, uri: Uri, secretKey: String): ByteArray {
//        val keySpec = SecretKeySpec(secretKey.toByteArray(), "AES")
//        val cipher = Cipher.getInstance("AES")
//        cipher.init(Cipher.DECRYPT_MODE, keySpec)
//
//        val encrypted = context.contentResolver.openInputStream(uri)?.readBytes()
//            ?: throw IOException("Cannot read file")
//
//        return cipher.doFinal(encrypted)
//    }

	fun generateCustomUUID(name: String?, phone: String?, secretKey: String): String {
		val input = "${name ?: "홍길동"}|${phone ?: "01000000000"}|$secretKey"
		val digest = MessageDigest.getInstance("SHA-256")
		val hashBytes = digest.digest(input.toByteArray())
		return hashBytes.joinToString("") { "%02x".format(it) }
	}
}