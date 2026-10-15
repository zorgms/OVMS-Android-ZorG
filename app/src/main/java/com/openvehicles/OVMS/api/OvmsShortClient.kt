package com.openvehicles.OVMS.api

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.openvehicles.OVMS.entities.CarData
import com.openvehicles.OVMS.utils.CarsStorage
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Arrays
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Lightweight client to perform short server connects to OVMS server.
 * Used for:
 * 1) Periodic polling (fetch status, update widgets, and disconnect).
 * 2) On-demand command execution (connect, send command, wait for response, and disconnect).
 *
 * This provides an alternative to the persistent TCP foreground service.
 */
object OvmsShortClient {

    private const val TAG = "OvmsShortClient"

    /**
     * Performs a short connect to fetch the latest car status and updates widgets.
     */
    fun fetchLatestData(context: Context, carData: CarData, timeoutMs: Long = 10000): Boolean {
        var session: OvmsSession? = null
        return try {
            Log.i(TAG, "fetchLatestData: connecting to ${carData.sel_server} for ${carData.sel_vehicleid}")
            session = OvmsSession(carData, timeoutMs)
            session.connectAndLogin()

            // Read messages until both Status (S) and Environment (D) are received or timeout
            val deadline = System.currentTimeMillis() + 4000
            var receivedStatus = false
            var receivedEnv = false

            while (System.currentTimeMillis() < deadline) {
                val msg = try {
                    session.readMessage()
                } catch (e: SocketTimeoutException) {
                    break
                } ?: break

                if (msg.startsWith("MP-0 ")) {
                    val payload = msg.substring(5)
                    if (payload.isNotEmpty()) {
                        val code = payload[0]
                        val data = payload.substring(1)
                        session.processPayload(code, data)
                        if (code == 'S') receivedStatus = true
                        if (code == 'D') receivedEnv = true
                        if (receivedStatus && receivedEnv) {
                            // Give a tiny window for remaining immediate records (T, L)
                            try {
                                val extra = session.readMessage()
                                if (extra != null && extra.startsWith("MP-0 ")) {
                                    val extraPayload = extra.substring(5)
                                    if (extraPayload.isNotEmpty()) {
                                        session.processPayload(extraPayload[0], extraPayload.substring(1))
                                    }
                                }
                            } catch (ignored: Exception) {}
                            break
                        }
                    }
                }
            }

            // Save updated cars and broadcast event
            CarsStorage.saveStoredCars()
            val intent = Intent(ApiService.ACTION_APIEVENT).apply {
                putExtra("event", "UpdateStatus")
                putExtra("isOnline", true)
            }
            context.sendBroadcast(intent)
            Log.i(TAG, "fetchLatestData: success, status updated for ${carData.sel_vehicleid}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "fetchLatestData failed: ${e.message}", e)
            false
        } finally {
            session?.close()
        }
    }

    /**
     * Connects, executes a command, waits for response, and disconnects.
     * Returns the command response payload (e.g. "0,OK" or error message), or null on failure.
     */
    fun executeCommand(
        context: Context,
        carData: CarData,
        command: String,
        timeoutMs: Long = 15000
    ): String? {
        var session: OvmsSession? = null
        return try {
            Log.i(TAG, "executeCommand: connecting to ${carData.sel_server} to send '$command'")
            session = OvmsSession(carData, timeoutMs)
            session.connectAndLogin()

            // Send command
            session.sendMessage("MP-0 C$command")
            Log.i(TAG, "executeCommand: command sent, waiting for response...")

            // Read until command response ('c') is received
            val deadline = System.currentTimeMillis() + timeoutMs
            var commandResponse: String? = null

            while (System.currentTimeMillis() < deadline) {
                val msg = try {
                    session.readMessage()
                } catch (e: SocketTimeoutException) {
                    break
                } ?: break

                if (msg.startsWith("MP-0 ")) {
                    val payload = msg.substring(5)
                    if (payload.isNotEmpty()) {
                        val code = payload[0]
                        val data = payload.substring(1)
                        session.processPayload(code, data)
                        if (code == 'c') {
                            commandResponse = data
                            Log.i(TAG, "executeCommand: received command response: $commandResponse")
                            break
                        }
                    }
                }
            }

            // Save any updated car data received during execution
            CarsStorage.saveStoredCars()
            val intent = Intent(ApiService.ACTION_APIEVENT).apply {
                putExtra("event", "UpdateStatus")
                putExtra("isOnline", true)
            }
            context.sendBroadcast(intent)

            commandResponse
        } catch (e: Exception) {
            Log.e(TAG, "executeCommand failed: ${e.message}", e)
            null
        } finally {
            session?.close()
        }
    }

    private class OvmsSession(
        private val carData: CarData,
        private val timeoutMs: Long
    ) {
        private var socket: Socket? = null
        private var reader: BufferedReader? = null
        private var writer: PrintWriter? = null
        private var rxCipher: Cipher? = null
        private var txCipher: Cipher? = null

        fun connectAndLogin() {
            val serverHost = carData.sel_server
            val useTls = carData.sel_tls
            val trustAllCerts = carData.sel_tls_trust_all
            val serverPort = if (useTls) 6870 else 6867
            val vehicleID = carData.sel_vehicleid
            val sharedSecret = carData.sel_server_password

            if (serverHost.isNullOrBlank() || vehicleID.isNullOrBlank() || sharedSecret.isNullOrBlank()) {
                throw IOException("Incomplete vehicle credentials for connection")
            }

            if (useTls) {
                val factory = if (trustAllCerts) {
                    val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    })
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, trustAll, SecureRandom())
                    sslContext.socketFactory
                } else {
                    javax.net.ssl.SSLSocketFactory.getDefault()
                }
                val sslSocket = factory.createSocket(serverHost, serverPort) as SSLSocket
                if (!trustAllCerts) {
                    val params = sslSocket.sslParameters
                    params.endpointIdentificationAlgorithm = "HTTPS"
                    sslSocket.sslParameters = params
                }
                socket = sslSocket
            } else {
                socket = Socket(serverHost, serverPort)
            }

            socket!!.soTimeout = timeoutMs.toInt()
            writer = PrintWriter(BufferedWriter(OutputStreamWriter(socket!!.getOutputStream())), true)
            reader = BufferedReader(InputStreamReader(socket!!.getInputStream()))

            // Generate 22-char random client token
            val b64tab = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray()
            val random = SecureRandom()
            val tokenBuilder = StringBuilder(22)
            for (i in 0..21) {
                tokenBuilder.append(b64tab[random.nextInt(b64tab.size)])
            }
            val clientTokenString = tokenBuilder.toString()
            val clientToken = clientTokenString.toByteArray()

            // Calculate client digest
            val clientHmac = Mac.getInstance("HmacMD5")
            val keySpec = SecretKeySpec(sharedSecret.toByteArray(), "HmacMD5")
            clientHmac.init(keySpec)
            val clientDigest = Base64.encodeToString(clientHmac.doFinal(clientToken), Base64.NO_WRAP)

            // Send login
            writer!!.println("MP-A 0 $clientTokenString $clientDigest $vehicleID")

            // Read welcome response
            val welcomeLine = reader!!.readLine() ?: throw IOException("Server disconnected during login")
            val welcomeParts = welcomeLine.trim().split("\\s+".toRegex()).toTypedArray()
            if (welcomeParts.size < 4 || welcomeParts[0] != "MP-S" || welcomeParts[1] != "0") {
                throw IOException("Server login rejected: $welcomeLine")
            }

            val serverTokenString = welcomeParts[2]
            val serverToken = serverTokenString.toByteArray()
            val serverDigest = Base64.decode(welcomeParts[3], 0)

            if (!Arrays.equals(clientHmac.doFinal(serverToken), serverDigest)) {
                throw IOException("Server authentication failed (digest mismatch)")
            }

            // Derive session key and initialize RC4
            val serverClientToken = serverTokenString + clientTokenString
            val clientKey = clientHmac.doFinal(serverClientToken.toByteArray())

            val rx = Cipher.getInstance("RC4")
            rx.init(Cipher.DECRYPT_MODE, SecretKeySpec(clientKey, "RC4"))
            val tx = Cipher.getInstance("RC4")
            tx.init(Cipher.ENCRYPT_MODE, SecretKeySpec(clientKey, "RC4"))

            // Prime ciphers with 1024 bytes
            val primeData = "0".repeat(1024).toByteArray()
            rx.update(primeData)
            tx.update(primeData)

            rxCipher = rx
            txCipher = tx
        }

        fun readMessage(): String? {
            val line = reader?.readLine() ?: return null
            val rx = line.trim()
            if (rx.isEmpty()) return ""
            val cipher = rxCipher ?: return null
            val decryptedBytes = cipher.update(Base64.decode(rx, 0))
            return String(decryptedBytes).trim()
        }

        fun sendMessage(message: String) {
            val cipher = txCipher ?: throw IOException("Cipher not initialized")
            val encrypted = cipher.update(message.toByteArray())
            val encoded = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            writer?.println(encoded)
        }

        fun processPayload(code: Char, data: String) {
            when (code) {
                'S' -> carData.processStatus(data)
                'D' -> carData.processEnvironment(data)
                'L' -> carData.processLocation(data)
                'F' -> carData.processFirmware(data)
                'V' -> carData.processCapabilities(data)
                'T' -> {
                    val raw = data.toLongOrNull() ?: 0L
                    carData.car_lastupdate_raw = raw
                    carData.car_lastupdated = Date(System.currentTimeMillis() - raw * 1000)
                }
                'Z' -> carData.server_carsconnected = data.toIntOrNull() ?: 0
            }
        }

        fun close() {
            try { writer?.close() } catch (ignored: Exception) {}
            try { reader?.close() } catch (ignored: Exception) {}
            try { socket?.close() } catch (ignored: Exception) {}
            writer = null
            reader = null
            socket = null
        }
    }
}
