package xyz.malefic.irc.server.tls

import java.io.File
import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/**
 * TLS/SSL configuration for the IRC server, loaded from environment variables.
 *
 * When no keystore path is configured, a self-signed certificate is generated
 * via `keytool` for development use. In production, supply a proper keystore.
 *
 * ## Environment Variables
 * | Variable | Default | Description |
 * |---|---|---|
 * | `IRC_TLS_ENABLED` | `false` | Enable the dedicated TLS listener |
 * | `IRC_TLS_PORT` | `6697` | Port for the dedicated TLS listener |
 * | `IRC_TLS_CERT_FILE` | — | Path to a PEM certificate file (e.g. `fullchain.pem`); use with `IRC_TLS_KEY_FILE` |
 * | `IRC_TLS_KEY_FILE` | — | Path to a PEM private key file (e.g. `privkey.pem`); use with `IRC_TLS_CERT_FILE` |
 * | `IRC_TLS_KEYSTORE_PATH` | — | Path to a JKS or PKCS12 (`.p12`/`.pfx`) keystore file (auto-generates via keytool if absent) |
 * | `IRC_TLS_KEYSTORE_PASSWORD` | `changeit` | Keystore password |
 * | `IRC_TLS_KEY_PASSWORD` | `changeit` | Private key password (JKS/PKCS12 only) |
 * | `IRC_TLS_KEY_ALIAS` | `irc` | Alias of the key entry in the keystore |
 *
 * @see SelfSignedCertificate for how development keystores are generated
 */
object TLSConfig {
    /** Whether the dedicated TLS port listener is enabled. */
    val tlsEnabled: Boolean = System.getenv("IRC_TLS_ENABLED")?.toBoolean() ?: false

    /** Port for the dedicated TLS listener (default: 6697 per RFC). */
    val tlsPort: Int = System.getenv("IRC_TLS_PORT")?.toIntOrNull() ?: 6697

    /**
     * Path to a PEM certificate file (e.g. `fullchain.pem`).
     * Must be set together with [keyFile]. Takes priority over [keystorePath].
     */
    val certFile: String? = System.getenv("IRC_TLS_CERT_FILE")

    /**
     * Path to a PEM private key file (e.g. `privkey.pem`).
     * Must be set together with [certFile]. Takes priority over [keystorePath].
     */
    val keyFile: String? = System.getenv("IRC_TLS_KEY_FILE")

    /** Path to the JKS or PKCS12 keystore file, or `null` to auto-generate a self-signed certificate. */
    val keystorePath: String? = System.getenv("IRC_TLS_KEYSTORE_PATH")

    /** Password used to open the keystore. */
    val keystorePassword: String = System.getenv("IRC_TLS_KEYSTORE_PASSWORD") ?: "changeit"

    /** Password protecting the private key entry. */
    val keyPassword: String = System.getenv("IRC_TLS_KEY_PASSWORD") ?: "changeit"

    /** Alias of the certificate/key entry within the keystore. */
    val keyAlias: String = System.getenv("IRC_TLS_KEY_ALIAS") ?: "irc"

    /**
     * Builds and returns an [SSLContext] configured from the cert source determined by environment
     * variables, in priority order:
     * 1. PEM files ([certFile] + [keyFile]) — converted to a temporary PKCS12 via `openssl`.
     * 2. Keystore file ([keystorePath]) — loaded as PKCS12 for `.p12`/`.pfx`, JKS otherwise.
     * 3. Auto-generated self-signed certificate via `keytool` (development only).
     *
     * For options 1 and 2 the returned [SSLContext] uses a [ReloadingKeyManager] that checks
     * for file changes every 60 seconds, so Let's Encrypt cert renewals are picked up
     * automatically without restarting the server.
     *
     * @return A ready-to-use [SSLContext], or `null` if TLS is not needed / cert loading fails.
     */
    fun createSSLContext(): SSLContext? {
        if (!tlsEnabled) return null

        return when {
            certFile != null && keyFile != null -> {
                val km = ReloadingKeyManager(
                    sourceFiles = listOf(File(certFile), File(keyFile)),
                    loadKeyStore = { PemCertificate.load(certFile, keyFile, keyAlias, keystorePassword) },
                    keyPassword = keystorePassword.toCharArray(),
                ) ?: return null
                SSLContext.getInstance("TLS").apply { init(arrayOf(km), null, null) }
            }
            keystorePath != null -> {
                val type = if (keystorePath.endsWith(".p12") || keystorePath.endsWith(".pfx")) "PKCS12" else "JKS"
                val km = ReloadingKeyManager(
                    sourceFiles = listOf(File(keystorePath)),
                    loadKeyStore = {
                        KeyStore.getInstance(type).apply {
                            File(keystorePath).inputStream().use { load(it, keystorePassword.toCharArray()) }
                        }
                    },
                    keyPassword = keyPassword.toCharArray(),
                ) ?: return null
                SSLContext.getInstance("TLS").apply { init(arrayOf(km), null, null) }
            }
            else -> {
                val ks = SelfSignedCertificate.generate(keyAlias, keystorePassword, "malefirc.local") ?: return null
                val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                kmf.init(ks, keystorePassword.toCharArray())
                SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
            }
        }
    }
}

/**
 * Generates a self-signed TLS certificate using the `keytool` CLI bundled with any JDK.
 *
 * The certificate is stored in a temporary JKS file that is automatically deleted on JVM exit.
 * It is valid for one year and uses RSA-2048 / SHA-256.
 *
 * WARNING: Self-signed certificates are not suitable for production use.
 * Use a CA-signed certificate via [TLSConfig.keystorePath] in production.
 *
 * Requires `keytool` to be available on the system PATH (included with any JDK installation).
 *
 * @see TLSConfig.createSSLContext
 */
internal object SelfSignedCertificate {
    /**
     * Generates a JKS [KeyStore] containing a new self-signed certificate using `keytool`.
     *
     * @param alias The key alias to use inside the keystore.
     * @param password The password used to protect both the keystore and the key entry.
     * @param cn The Common Name (CN) placed in the certificate's subject (typically the server hostname).
     * @return A [KeyStore] containing the self-signed certificate and private key, or `null` on failure.
     */
    fun generate(
        alias: String,
        password: String,
        cn: String,
    ): KeyStore? =
        try {
            val tmpFile = File.createTempFile("malefirc-tls-", ".jks")
            tmpFile.deleteOnExit()

            val process =
                ProcessBuilder(
                    "keytool", "-genkeypair",
                    "-alias", alias,
                    "-keyalg", "RSA", "-keysize", "2048",
                    "-sigalg", "SHA256withRSA",
                    "-dname", "CN=$cn,O=Malefirc,C=US",
                    "-validity", "365",
                    "-keystore", tmpFile.absolutePath,
                    "-storepass", password,
                    "-keypass", password,
                    "-noprompt",
                ).redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                System.err.println("keytool failed (exit $exitCode): $output")
                null
            } else {
                KeyStore.getInstance("JKS").apply {
                    tmpFile.inputStream().use { load(it, password.toCharArray()) }
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to generate self-signed certificate via keytool: ${e.message}")
            null
        }
}

/**
 * Loads a TLS certificate from PEM files using the `openssl` CLI.
 *
 * The PEM cert and key are combined into a temporary PKCS12 file that is automatically
 * deleted on JVM exit. Requires `openssl` to be available on the system PATH.
 *
 * @see TLSConfig.createSSLContext
 */
internal object PemCertificate {
    /**
     * Converts PEM [certFile] + [keyFile] into a PKCS12 [KeyStore] using `openssl pkcs12 -export`.
     *
     * @param certFile Path to the PEM certificate file (e.g. `fullchain.pem`).
     * @param keyFile Path to the PEM private key file (e.g. `privkey.pem`).
     * @param alias The key alias to use inside the resulting keystore.
     * @param password The password used to protect both the keystore and the key entry.
     * @return A [KeyStore] containing the certificate and private key, or `null` on failure.
     */
    fun load(
        certFile: String,
        keyFile: String,
        alias: String,
        password: String,
    ): KeyStore? =
        try {
            val tmpFile = File.createTempFile("malefirc-tls-", ".p12")
            tmpFile.deleteOnExit()

            val process =
                ProcessBuilder(
                    "openssl", "pkcs12", "-export",
                    "-in", certFile,
                    "-inkey", keyFile,
                    "-out", tmpFile.absolutePath,
                    "-passout", "pass:$password",
                    "-name", alias,
                ).redirectErrorStream(true)
                    .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            if (exitCode != 0) {
                System.err.println("openssl failed (exit $exitCode): $output")
                null
            } else {
                KeyStore.getInstance("PKCS12").apply {
                    tmpFile.inputStream().use { load(it, password.toCharArray()) }
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to load PEM certificate via openssl: ${e.message}")
            null
        }
}

/**
 * An [X509ExtendedKeyManager] that transparently reloads the underlying certificate when
 * any of the [sourceFiles] change on disk.
 *
 * The modification time of each source file is checked at most once per [checkIntervalMs]
 * (default: 60 seconds) to avoid filesystem overhead on every TLS handshake. When a change
 * is detected, [loadKeyStore] is called and the delegate key manager is replaced atomically.
 * In-flight connections are unaffected; new handshakes immediately use the fresh certificate.
 *
 * Returns `null` from the factory function if the initial load fails.
 */
internal class ReloadingKeyManager private constructor(
    private val sourceFiles: List<File>,
    private val loadKeyStore: () -> KeyStore?,
    private val keyPassword: CharArray,
    private val checkIntervalMs: Long,
    initialDelegate: X509ExtendedKeyManager,
) : X509ExtendedKeyManager() {

    companion object {
        operator fun invoke(
            sourceFiles: List<File>,
            loadKeyStore: () -> KeyStore?,
            keyPassword: CharArray,
            checkIntervalMs: Long = 60_000L,
        ): ReloadingKeyManager? {
            val delegate = buildDelegate(loadKeyStore, keyPassword) ?: return null
            return ReloadingKeyManager(sourceFiles, loadKeyStore, keyPassword, checkIntervalMs, delegate)
        }

        private fun buildDelegate(loadKeyStore: () -> KeyStore?, keyPassword: CharArray): X509ExtendedKeyManager? {
            val ks = loadKeyStore() ?: return null
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(ks, keyPassword)
            return kmf.keyManagers.filterIsInstance<X509ExtendedKeyManager>().firstOrNull()
        }
    }

    @Volatile private var delegate: X509ExtendedKeyManager = initialDelegate
    @Volatile private var lastChecked: Long = System.currentTimeMillis()
    @Volatile private var lastModified: Long = maxMtime()

    private fun maxMtime() = sourceFiles.maxOfOrNull { it.lastModified() } ?: 0L

    private fun maybeReload() {
        val now = System.currentTimeMillis()
        if (now - lastChecked < checkIntervalMs) return
        lastChecked = now
        val mtime = maxMtime()
        if (mtime == lastModified) return
        buildDelegate(loadKeyStore, keyPassword)
            ?.let {
                delegate = it
                lastModified = mtime
                println("TLS certificate reloaded (file change detected)")
            }
            ?: System.err.println("TLS certificate reload failed — continuing with previous certificate")
    }

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        delegate.getClientAliases(keyType, issuers)

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? =
        delegate.chooseClientAlias(keyType, issuers, socket)

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        delegate.getServerAliases(keyType, issuers)

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? {
        maybeReload()
        return delegate.chooseServerAlias(keyType, issuers, socket)
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        delegate.getCertificateChain(alias)

    override fun getPrivateKey(alias: String?): PrivateKey? =
        delegate.getPrivateKey(alias)

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? {
        maybeReload()
        return delegate.chooseEngineServerAlias(keyType, issuers, engine)
    }

    override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        delegate.chooseEngineClientAlias(keyType, issuers, engine)
}
