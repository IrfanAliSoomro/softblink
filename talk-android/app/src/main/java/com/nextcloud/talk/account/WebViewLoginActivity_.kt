// /*
//  * Nextcloud Talk - Android Client
//  *
//  * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
//  * SPDX-License-Identifier: GPL-3.0-or-later
//  */
// package com.nextcloud.talk.account
//
// import android.annotation.SuppressLint
// import android.content.Intent
// import android.content.pm.ActivityInfo
// import android.graphics.Bitmap
// import android.net.http.SslError
// import android.os.Build
// import android.os.Bundle
// import android.security.KeyChain
// import android.security.KeyChainException
// import android.text.TextUtils
// import android.util.Log
// import android.view.View
// import android.webkit.ClientCertRequest
// import android.webkit.CookieSyncManager
// import android.webkit.SslErrorHandler
// import android.webkit.WebResourceRequest
// import android.webkit.WebResourceResponse
// import android.webkit.WebSettings
// import android.webkit.WebView
// import android.webkit.WebViewClient
// import androidx.activity.OnBackPressedCallback
// import androidx.work.OneTimeWorkRequest
// import androidx.work.WorkInfo
// import androidx.work.WorkManager
// import autodagger.AutoInjector
// import com.google.android.material.snackbar.Snackbar
// import com.nextcloud.talk.R
// import android.widget.Toast
// import com.nextcloud.talk.activities.BaseActivity
// import com.nextcloud.talk.activities.MainActivity
// import com.nextcloud.talk.account.AccountVerificationActivity
// import com.nextcloud.talk.application.NextcloudTalkApplication
// import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
// import com.nextcloud.talk.databinding.ActivityWebViewLoginBinding
// import com.nextcloud.talk.events.CertificateEvent
// import com.nextcloud.talk.jobs.AccountRemovalWorker
// import com.nextcloud.talk.models.LoginData
// import com.nextcloud.talk.users.UserManager
// import com.nextcloud.talk.utils.bundle.BundleKeys
// import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_BASE_URL
// import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_ORIGINAL_PROTOCOL
// import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_TOKEN
// import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USERNAME
// import com.nextcloud.talk.utils.ssl.TrustManager
// import de.cotech.hw.fido.WebViewFidoBridge
// import de.cotech.hw.fido2.WebViewWebauthnBridge
// import de.cotech.hw.fido2.ui.WebauthnDialogOptions
// import io.reactivex.disposables.Disposable
// import java.lang.reflect.Field
// import java.net.CookieManager
// import java.net.URLDecoder
// import java.security.PrivateKey
// import java.security.cert.CertificateException
// import java.security.cert.X509Certificate
// import java.util.Locale
// import java.util.concurrent.CountDownLatch
// import java.util.concurrent.TimeUnit
// import javax.inject.Inject
// import okhttp3.OkHttpClient
// import okhttp3.Request
// import okhttp3.RequestBody
// import okhttp3.FormBody
// import com.google.gson.JsonParser
// import com.google.gson.JsonObject
//
// @Suppress("ReturnCount", "LongMethod")
// @AutoInjector(NextcloudTalkApplication::class)
// class WebViewLoginActivity : BaseActivity() {
//
//     private lateinit var binding: ActivityWebViewLoginBinding
//
//     @Inject
//     lateinit var userManager: UserManager
//
//     @Inject
//     lateinit var trustManager: TrustManager
//
//     @Inject
//     lateinit var cookieManager: CookieManager
//
//     private var assembledPrefix: String? = null
//     private var userQueryDisposable: Disposable? = null
//     private var baseUrl: String? = null
//     private var reauthorizeAccount = false
//     private var username: String? = null
//     private var password: String? = null
//     private var loginStep = 0
//     private var automatedLoginAttempted = false
//     private var webViewFidoBridge: WebViewFidoBridge? = null
//     private var webViewWebauthnBridge: WebViewWebauthnBridge? = null
//
//     private val onBackPressedCallback = object : OnBackPressedCallback(true) {
//         override fun handleOnBackPressed() {
//             // Check if login was successful before going back
//             checkForLoginSuccess()
//
//             // If login wasn't successful, go back to main activity
//             val intent = Intent(context, MainActivity::class.java)
//             intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//             startActivity(intent)
//         }
//     }
//     private val webLoginUserAgent: String
//         get() = (
//             Build.MANUFACTURER.substring(0, 1).uppercase(Locale.getDefault()) +
//                 Build.MANUFACTURER.substring(1).uppercase(Locale.getDefault()) +
//                 " " +
//                 Build.MODEL +
//                 " (" +
//                 resources!!.getString(R.string.nc_app_product_name) +
//                 ")"
//             )
//
//     @SuppressLint("SourceLockedOrientationActivity")
//     override fun onCreate(savedInstanceState: Bundle?) {
//         super.onCreate(savedInstanceState)
//         sharedApplication!!.componentApplication.inject(this)
//         binding = ActivityWebViewLoginBinding.inflate(layoutInflater)
//         requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
//         setContentView(binding.root)
//         actionBar?.hide()
//         initSystemBars()
//
//         onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
//         handleIntent()
//         setupWebView()
//     }
//
//     private fun fetchAndLoadLoginUrl() {
//         if (baseUrl == null) {
//             Log.e(TAG, "Base URL is null")
//             return
//         }
//
//         // Show progress bar while fetching
//         binding.progressBar.visibility = View.VISIBLE
//         binding.webview.visibility = View.INVISIBLE
//
//         // Make API call to get login URL
//         val loginApiUrl = "$baseUrl/index.php/login/v2"
//         val request = Request.Builder()
//             .url(loginApiUrl)
//             .post(FormBody.Builder().build())
//             .addHeader("Clear-Site-Data", "cookies")
//             .build()
//
//         // Use a background thread for network call
//         Thread {
//             try {
//                 val client = OkHttpClient.Builder().build()
//                 val response = client.newCall(request).execute()
//
//                 if (response.isSuccessful) {
//                     val responseBody = response.body?.string()
//                     if (responseBody != null) {
//                         val jsonObject = JsonParser.parseString(responseBody).asJsonObject
//                         val loginUrl = jsonObject.get("login")?.asString
//
//                         if (!loginUrl.isNullOrEmpty()) {
//                             // Load the login URL in WebView on main thread
//                             runOnUiThread {
//                                 binding.progressBar.visibility = View.GONE
//                                 binding.webview.visibility = View.VISIBLE
//                                 binding.webview.loadUrl(loginUrl)
//                             }
//                         } else {
//                             Log.e(TAG, "Login URL is null or empty in API response")
//                             showError("Failed to get login URL from server")
//                         }
//                     }
//                 } else {
//                     Log.e(TAG, "API call failed with code: ${response.code}")
//                     showError("Server error: ${response.code}")
//                 }
//             } catch (e: Exception) {
//                 Log.e(TAG, "Error fetching login URL", e)
//                 showError("Network error: ${e.message}")
//             }
//         }.start()
//     }
//
//     private fun showError(message: String) {
//         runOnUiThread {
//             binding.progressBar.visibility = View.GONE
//             // You can add a TextView to show error messages if needed
//             Toast.makeText(this@WebViewLoginActivity, message, Toast.LENGTH_LONG).show()
//         }
//     }
//
//     private fun handleIntent() {
//         val extras = intent.extras!!
//         baseUrl = extras.getString(KEY_BASE_URL)
//         username = extras.getString(KEY_USERNAME)
//
//         if (extras.containsKey(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)) {
//             reauthorizeAccount = extras.getBoolean(BundleKeys.KEY_REAUTHORIZE_ACCOUNT)
//         }
//
//         if (extras.containsKey(BundleKeys.KEY_PASSWORD)) {
//             password = extras.getString(BundleKeys.KEY_PASSWORD)
//         }
//     }
//
//     private fun handleQRCodeLogin(qrData: String) {
//         // Parse QR code data and handle login
//         // This will be implemented based on your QR code format
//         Log.d(TAG, "QR Code login data: $qrData")
//         // For now, just proceed with normal login flow
//     }
//
//     private fun handleSuccessfulLogin(url: String) {
//         Log.d(TAG, "Login successful, navigating to next screen. URL: $url")
//
//         // Extract login data from the successful login
//         // This will depend on how your server indicates successful login
//         // For now, we'll proceed to account verification with the base URL
//
//         val bundle = Bundle()
//         bundle.putString(KEY_BASE_URL, baseUrl)
//
//         // Extract credentials using multiple methods
//         val (extractedUsername, extractedPassword) = extractCredentialsFromForm()
//         val (capturedUsername, capturedPassword) = getCapturedCredentials()
//
//         // Use the best available credentials
//         val finalUsername = extractedUsername ?: capturedUsername ?: username
//         val finalPassword = extractedPassword ?: capturedPassword ?: password
//
//         Log.d(TAG, "Credential extraction results:")
//         Log.d(TAG, "  Form extraction: Username=$extractedUsername, Password=${if (extractedPassword.isNullOrEmpty()) "null" else "***"}")
//         Log.d(TAG, "  Captured: Username=$capturedUsername, Password=${if (capturedPassword.isNullOrEmpty()) "null" else "***"}")
//         Log.d(TAG, "  Stored: Username=$username, Password=${if (password.isNullOrEmpty()) "null" else "***"}")
//         Log.d(TAG, "  Final: Username=$finalUsername, Password=${if (finalPassword.isNullOrEmpty()) "null" else "***"}")
//
//         if (!TextUtils.isEmpty(finalUsername) && !TextUtils.isEmpty(finalPassword)) {
//             bundle.putString(KEY_USERNAME, finalUsername)
//             bundle.putString(KEY_TOKEN, finalPassword)
//
//             // Navigate to account verification activity
//             val intent = Intent(this@WebViewLoginActivity, AccountVerificationActivity::class.java)
//             intent.putExtras(bundle)
//             intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//             startActivity(intent)
//             finish() // Close this activity
//         } else {
//             Log.e(TAG, "Failed to extract valid credentials")
//             Toast.makeText(this@WebViewLoginActivity, "Failed to extract login credentials. Please try again.", Toast.LENGTH_LONG).show()
//         }
//     }
//
//     private fun setupCredentialCapture() {
//         // Inject JavaScript to capture credentials as they're entered
//         binding.webview.evaluateJavascript("(function() { " +
//             "window.capturedCredentials = {username: '', password: ''}; " +
//             "console.log('Credential capture system initialized'); " +
//             "  " +
//             "// Capture username as user types " +
//             "document.addEventListener('input', function(e) { " +
//             "  if (e.target.type === 'text' || e.target.type === 'email') { " +
//             "    window.capturedCredentials.username = e.target.value; " +
//             "    console.log('Username captured:', e.target.value); " +
//             "  } " +
//             "  if (e.target.type === 'password') { " +
//             "    window.capturedCredentials.password = e.target.value; " +
//             "    console.log('Password captured: ***'); " +
//             "  } " +
//             "}); " +
//             "  " +
//             "// Also capture on form submission " +
//             "document.addEventListener('submit', function(e) { " +
//             "  var form = e.target; " +
//             "  var usernameInput = form.querySelector('input[type=\"text\"], input[type=\"email\"]'); " +
//             "  var passwordInput = form.querySelector('input[type=\"password\"]'); " +
//             "  if (usernameInput) window.capturedCredentials.username = usernameInput.value; " +
//             "  if (passwordInput) window.capturedCredentials.password = passwordInput.value; " +
//             "  console.log('Form submitted - Final credentials captured'); " +
//             "}); " +
//             "  " +
//             "return 'Credential capture system ready'; " +
//             "})();") { result ->
//             Log.d(TAG, "Credential capture setup result: $result")
//         }
//     }
//
//     private fun getCapturedCredentials(): Pair<String?, String?> {
//         var capturedUsername: String? = null
//         var capturedPassword: String? = null
//
//         // Use a synchronous approach to get credentials
//         val latch = CountDownLatch(1)
//
//         binding.webview.evaluateJavascript("(function() { " +
//             "if (window.capturedCredentials) { " +
//             "  return JSON.stringify(window.capturedCredentials); " +
//             "} " +
//             "return null; " +
//             "})();") { result ->
//             try {
//                 if (result != null && result != "null") {
//                     val jsonResult = result.trim('"').replace("\\\"", "\"")
//                     val jsonObject = JsonParser.parseString(jsonResult).asJsonObject
//                     capturedUsername = jsonObject.get("username")?.asString
//                     capturedPassword = jsonObject.get("password")?.asString
//
//                     Log.d(TAG, "Captured credentials - Username: $capturedUsername, Password: ${if (capturedPassword.isNullOrEmpty()) "null" else "***"}")
//                 }
//             } catch (e: Exception) {
//                 Log.e(TAG, "Error parsing captured credentials", e)
//             } finally {
//                 latch.countDown()
//             }
//         }
//
//         try {
//             latch.await(2, TimeUnit.SECONDS) // Wait up to 2 seconds
//         } catch (e: InterruptedException) {
//             Log.e(TAG, "Interrupted while waiting for credentials", e)
//         }
//
//         return Pair(capturedUsername, capturedPassword)
//     }
//
//     private fun extractCredentialsFromForm(): Pair<String?, String?> {
//         var extractedUsername: String? = null
//         var extractedPassword: String? = null
//
//         val latch = CountDownLatch(1)
//
//         binding.webview.evaluateJavascript("(function() { " +
//             "var username = ''; " +
//             "var password = ''; " +
//             "try { " +
//             "  // Try multiple field selectors " +
//             "  var userField = document.getElementById('user') || " +
//             "                  document.getElementById('username') || " +
//             "                  document.getElementById('login') || " +
//             "                  document.getElementById('email') || " +
//             "                  document.querySelector('input[type=\"text\"]') || " +
//             "                  document.querySelector('input[name*=\"user\"]') || " +
//             "                  document.querySelector('input[name*=\"login\"]'); " +
//             "  " +
//             "  var passField = document.getElementById('password') || " +
//             "                  document.getElementById('pass') || " +
//             "                  document.getElementById('pwd') || " +
//             "                  document.querySelector('input[type=\"password\"]') || " +
//             "                  document.querySelector('input[name*=\"pass\"]') || " +
//             "                  document.querySelector('input[name*=\"pwd\"]'); " +
//             "  " +
//             "  if (userField && userField.value) { " +
//             "    username = userField.value; " +
//             "    console.log('Found username field:', userField.id || userField.name, 'value:', username); " +
//             "  } " +
//             "  if (passField && passField.value) { " +
//             "    password = passField.value; " +
//             "    console.log('Found password field:', passField.id || passField.name, 'value: ***'); " +
//             "  } " +
//             "  " +
//             "  // Fallback: scan all inputs " +
//             "  if (!username || !password) { " +
//             "    var allInputs = document.querySelectorAll('input'); " +
//             "    for (var i = 0; i < allInputs.length; i++) { " +
//             "      var input = allInputs[i]; " +
//             "      if (input.type === 'text' && !username && input.value) { " +
//             "        username = input.value; " +
//             "      } " +
//             "      if (input.type === 'password' && !password && input.value) { " +
//             "        password = input.value; " +
//             "      } " +
//             "    } " +
//             "  } " +
//             "} catch(e) { " +
//             "  console.log('Error extracting credentials:', e); " +
//             "} " +
//             "return JSON.stringify({username: username, password: password}); " +
//             "})();") { result ->
//             try {
//                 if (result != null && result != "null") {
//                     val jsonResult = result.trim('"').replace("\\\"", "\"")
//                     val jsonObject = JsonParser.parseString(jsonResult).asJsonObject
//                     extractedUsername = jsonObject.get("username")?.asString
//                     extractedPassword = jsonObject.get("password")?.asString
//
//                     Log.d(TAG, "Form extraction result - Username: $extractedUsername, Password: ${if (extractedPassword.isNullOrEmpty()) "null" else "***"}")
//                 }
//             } catch (e: Exception) {
//                 Log.e(TAG, "Error parsing extracted credentials", e)
//             } finally {
//                 latch.countDown()
//             }
//         }
//
//         try {
//             latch.await(2, TimeUnit.SECONDS)
//         } catch (e: InterruptedException) {
//             Log.e(TAG, "Interrupted while waiting for form extraction", e)
//         }
//
//         return Pair(extractedUsername, extractedPassword)
//     }
//
//     private fun testCredentialExtraction() {
//         Log.d(TAG, "Testing credential extraction...")
//
//         val (formUsername, formPassword) = extractCredentialsFromForm()
//         val (capturedUsername, capturedPassword) = getCapturedCredentials()
//
//         Log.d(TAG, "Test Results:")
//         Log.d(TAG, "  Form extraction: Username=$formUsername, Password=${if (formPassword.isNullOrEmpty()) "null" else "***"}")
//         Log.d(TAG, "  Captured: Username=$capturedUsername, Password=${if (capturedPassword.isNullOrEmpty()) "null" else "***"}")
//         Log.d(TAG, "  Stored: Username=$username, Password=${if (password.isNullOrEmpty()) "null" else "***"}")
//
//         // Show toast with results
//         val message = "Form: ${formUsername ?: "null"}, Captured: ${capturedUsername ?: "null"}"
//         Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
//     }
//
//     private fun startLoginSuccessMonitoring() {
//         // Monitor for successful login by checking page content periodically
//         val handler = android.os.Handler(android.os.Looper.getMainLooper())
//         val runnable = object : Runnable {
//             override fun run() {
//                 if (binding.webview.visibility == View.VISIBLE) {
//                     checkForLoginSuccess()
//                 }
//                 // Continue monitoring every 2 seconds
//                 handler.postDelayed(this, 2000)
//             }
//         }
//         handler.postDelayed(runnable, 2000)
//     }
//
//     private fun checkForLoginSuccess() {
//         binding.webview.evaluateJavascript("(function() { " +
//             "var bodyText = document.body.innerText || document.body.textContent || ''; " +
//             "var hasSuccess = bodyText.includes('Account connected') || " +
//             "bodyText.includes('successfully') || " +
//             "bodyText.includes('Welcome') || " +
//             "bodyText.includes('Dashboard') || " +
//             "bodyText.includes('You can close this window') || " +
//             "bodyText.includes('client should now be connected'); " +
//             "return hasSuccess; " +
//             "})();") { result ->
//             if (result == "true") {
//                 // Stop monitoring and handle successful login
//                 android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacksAndMessages(null)
//                 handleSuccessfulLogin(binding.webview.url ?: "")
//             }
//         }
//     }
//
//     @SuppressLint("SetJavaScriptEnabled")
//     private fun setupWebView() {
//         assembledPrefix = resources!!.getString(R.string.nc_talk_login_scheme) + PROTOCOL_SUFFIX + "login/"
//         binding.webview.settings.allowFileAccess = false
//         binding.webview.settings.allowFileAccessFromFileURLs = false
//         binding.webview.settings.javaScriptEnabled = true
//         binding.webview.settings.javaScriptCanOpenWindowsAutomatically = false
//         binding.webview.settings.domStorageEnabled = true
//         binding.webview.settings.userAgentString = webLoginUserAgent
//         // Temporarily enable form data saving for credential capture
//         binding.webview.settings.saveFormData = true
//         binding.webview.settings.savePassword = true
//         binding.webview.settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
//         binding.webview.clearCache(true)
//         binding.webview.clearFormData()
//         binding.webview.clearHistory()
//         WebView.clearClientCertPreferences(null)
//         webViewFidoBridge = WebViewFidoBridge.createInstanceForWebView(this, binding.webview)
//
//         val webauthnOptionsBuilder = WebauthnDialogOptions.builder().setShowSdkLogo(true).setAllowSkipPin(true)
//         webViewWebauthnBridge = WebViewWebauthnBridge.createInstanceForWebView(
//             this,
//             binding.webview,
//             webauthnOptionsBuilder
//         )
//
//         CookieSyncManager.createInstance(this)
//         android.webkit.CookieManager.getInstance().removeAllCookies(null)
//         val headers: MutableMap<String, String> = HashMap()
//         headers["OCS-APIRequest"] = "true"
//
//         // Fetch login URL from API and load it directly
//         fetchAndLoadLoginUrl()
//
//         // Start monitoring for successful login
//         startLoginSuccessMonitoring()
//
//         // Setup credential capture system
//         setupCredentialCapture()
//
//         // Add a test button for debugging credential extraction
//         binding.root.post {
//             // Test credential extraction after a short delay
//             binding.root.postDelayed({
//                 testCredentialExtraction()
//             }, 3000) // Test after 3 seconds
//         }
//
//         binding.webview.webViewClient = object : WebViewClient() {
//             private var basePageLoaded = false
//             override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
//                 webViewFidoBridge?.delegateShouldInterceptRequest(view, request)
//                 webViewWebauthnBridge?.delegateShouldInterceptRequest(view, request)
//                 return super.shouldInterceptRequest(view, request)
//             }
//
//             override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
//                 super.onPageStarted(view, url, favicon)
//                 webViewFidoBridge?.delegateOnPageStarted(view, url, favicon)
//                 webViewWebauthnBridge?.delegateOnPageStarted(view, url, favicon)
//             }
//
//             @Deprecated("Use shouldOverrideUrlLoading(WebView view, WebResourceRequest request)")
//             override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
//                 if (url.startsWith(assembledPrefix!!)) {
//                     parseAndLoginFromWebView(url)
//                     return true
//                 }
//
//                 // Check if login was successful by looking for success indicators in the URL
//                 if (url.contains("success") || url.contains("connected") || url.contains("dashboard")) {
//                     handleSuccessfulLogin(url)
//                     return true
//                 }
//
//                 return false
//             }
//
//             @Suppress("Detekt.TooGenericExceptionCaught")
//             override fun onPageFinished(view: WebView, url: String) {
//                 loginStep++
//                 if (!basePageLoaded) {
//                     binding.progressBar.visibility = View.GONE
//                     binding.webview.visibility = View.VISIBLE
//
//                     basePageLoaded = true
//                 }
//
//                 // Check if login was successful by examining the page content
//                 if (url.contains("success") || url.contains("connected") || url.contains("dashboard")) {
//                     handleSuccessfulLogin(url)
//                     return
//                 }
//
//                 // Check for success indicators in the page content
//                 view.evaluateJavascript("(function() { " +
//                     "var bodyText = document.body.innerText || document.body.textContent || ''; " +
//                     "var hasSuccess = bodyText.includes('Account connected') || " +
//                     "bodyText.includes('successfully') || " +
//                     "bodyText.includes('Welcome') || " +
//                     "bodyText.includes('Dashboard'); " +
//                     "return hasSuccess; " +
//                     "})();") { result ->
//                     if (result == "true") {
//                         handleSuccessfulLogin(url)
//                     }
//                 }
//
//                 if (!TextUtils.isEmpty(username)) {
//                     if (loginStep == 1) {
//                         binding.webview.loadUrl(
//                             "javascript: {document.getElementsByClassName('login')[0].click(); };"
//                         )
//                     } else if (!automatedLoginAttempted) {
//                         automatedLoginAttempted = true
//                         if (TextUtils.isEmpty(password)) {
//                             binding.webview.loadUrl(
//                                 "javascript:var justStore = document.getElementById('user').value = '$username';"
//                             )
//                         } else {
//                             binding.webview.loadUrl(
//                                 "javascript: {" +
//                                     "document.getElementById('user').value = '" + username + "';" +
//                                     "document.getElementById('password').value = '" + password + "';" +
//                                     "document.getElementById('submit').click(); };"
//                             )
//                         }
//                     }
//                 }
//
//                 super.onPageFinished(view, url)
//             }
//
//             override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
//                 var alias: String? = null
//                 if (!reauthorizeAccount) {
//                     alias = appPreferences.temporaryClientCertAlias
//                 }
//                 val user = currentUserProvider.currentUser.blockingGet()
//                 if (TextUtils.isEmpty(alias) && user != null) {
//                     alias = user.clientCertificate
//                 }
//                 if (!TextUtils.isEmpty(alias)) {
//                     val finalAlias = alias
//                     Thread {
//                         try {
//                             val privateKey = KeyChain.getPrivateKey(applicationContext, finalAlias!!)
//                             val certificates = KeyChain.getCertificateChain(
//                                 applicationContext,
//                                 finalAlias
//                             )
//                             if (privateKey != null && certificates != null) {
//                                 request.proceed(privateKey, certificates)
//                             } else {
//                                 request.cancel()
//                             }
//                         } catch (e: KeyChainException) {
//                             request.cancel()
//                         } catch (e: InterruptedException) {
//                             request.cancel()
//                         }
//                     }.start()
//                 } else {
//                     KeyChain.choosePrivateKeyAlias(
//                         this@WebViewLoginActivity,
//                         { chosenAlias: String? ->
//                             if (chosenAlias != null) {
//                                 appPreferences!!.temporaryClientCertAlias = chosenAlias
//                                 Thread {
//                                     var privateKey: PrivateKey? = null
//                                     try {
//                                         privateKey = KeyChain.getPrivateKey(applicationContext, chosenAlias)
//                                         val certificates = KeyChain.getCertificateChain(
//                                             applicationContext,
//                                             chosenAlias
//                                         )
//                                         if (privateKey != null && certificates != null) {
//                                             request.proceed(privateKey, certificates)
//                                         } else {
//                                             request.cancel()
//                                         }
//                                     } catch (e: KeyChainException) {
//                                         request.cancel()
//                                     } catch (e: InterruptedException) {
//                                         request.cancel()
//                                     }
//                                 }.start()
//                             } else {
//                                 request.cancel()
//                             }
//                         },
//                         arrayOf("RSA", "EC"),
//                         null,
//                         request.host,
//                         request.port,
//                         null
//                     )
//                 }
//             }
//
//             @SuppressLint("DiscouragedPrivateApi")
//             @Suppress("Detekt.TooGenericExceptionCaught", "WebViewClientOnReceivedSslError")
//             override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
//                 try {
//                     val sslCertificate = error.certificate
//                     val f: Field = sslCertificate.javaClass.getDeclaredField("mX509Certificate")
//                     f.isAccessible = true
//                     val cert = f[sslCertificate] as X509Certificate
//                     try {
//                         trustManager.checkServerTrusted(arrayOf(cert), "generic")
//                         handler.proceed()
//                     } catch (exception: CertificateException) {
//                         eventBus.post(CertificateEvent(cert, trustManager, handler))
//                     }
//                 } catch (exception: Exception) {
//                     handler.cancel()
//                 }
//             }
//
//             @Deprecated("Deprecated in super implementation")
//             override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
//                 super.onReceivedError(view, errorCode, description, failingUrl)
//             }
//         }
//         binding.webview.loadUrl("$baseUrl/index.php/login/flow", headers)
//     }
//
//     private fun dispose() {
//         if (userQueryDisposable != null && !userQueryDisposable!!.isDisposed) {
//             userQueryDisposable!!.dispose()
//         }
//         userQueryDisposable = null
//     }
//
//     private fun parseAndLoginFromWebView(dataString: String) {
//         val loginData = parseLoginData(assembledPrefix, dataString)
//         if (loginData != null) {
//             dispose()
//             cookieManager.cookieStore.removeAll()
//
//             if (userManager.checkIfUserIsScheduledForDeletion(loginData.username!!, baseUrl!!).blockingGet()) {
//                 Log.e(TAG, "Tried to add already existing user who is scheduled for deletion.")
//                 Snackbar.make(binding.root, R.string.nc_common_error_sorry, Snackbar.LENGTH_LONG).show()
//                 // however the user is not yet deleted, just start AccountRemovalWorker again to make sure to delete it.
//                 startAccountRemovalWorkerAndRestartApp()
//             } else if (userManager.checkIfUserExists(loginData.username!!, baseUrl!!).blockingGet()) {
//                 if (reauthorizeAccount) {
//                     updateUserAndRestartApp(loginData)
//                 } else {
//                     Log.w(TAG, "It was tried to add an account that account already exists. Skipped user creation.")
//                     restartApp()
//                 }
//             } else {
//                 startAccountVerification(loginData)
//             }
//         }
//     }
//
//     private fun startAccountVerification(loginData: LoginData) {
//         val bundle = Bundle()
//         bundle.putString(KEY_USERNAME, loginData.username)
//         bundle.putString(KEY_TOKEN, loginData.token)
//         bundle.putString(KEY_BASE_URL, loginData.serverUrl)
//         var protocol = ""
//         if (baseUrl!!.startsWith("http://")) {
//             protocol = "http://"
//         } else if (baseUrl!!.startsWith("https://")) {
//             protocol = "https://"
//         }
//         if (!TextUtils.isEmpty(protocol)) {
//             bundle.putString(KEY_ORIGINAL_PROTOCOL, protocol)
//         }
//         val intent = Intent(context, AccountVerificationActivity::class.java)
//         intent.putExtras(bundle)
//         startActivity(intent)
//     }
//
//     private fun restartApp() {
//         val intent = Intent(context, MainActivity::class.java)
//         intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//         startActivity(intent)
//     }
//
//     private fun updateUserAndRestartApp(loginData: LoginData) {
//         val currentUser = currentUserProvider.currentUser.blockingGet()
//         if (currentUser != null) {
//             currentUser.clientCertificate = appPreferences.temporaryClientCertAlias
//             currentUser.token = loginData.token
//             val rowsUpdated = userManager.updateOrCreateUser(currentUser).blockingGet()
//             Log.d(TAG, "User rows updated: $rowsUpdated")
//             restartApp()
//         }
//     }
//
//     private fun startAccountRemovalWorkerAndRestartApp() {
//         val accountRemovalWork = OneTimeWorkRequest.Builder(AccountRemovalWorker::class.java).build()
//         WorkManager.getInstance(applicationContext).enqueue(accountRemovalWork)
//
//         WorkManager.getInstance(context).getWorkInfoByIdLiveData(accountRemovalWork.id)
//             .observeForever { workInfo: WorkInfo? ->
//
//                 when (workInfo?.state) {
//                     WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
//                         restartApp()
//                     }
//
//                     else -> {}
//                 }
//             }
//     }
//
//     private fun parseLoginData(prefix: String?, dataString: String): LoginData? {
//         if (dataString.length < prefix!!.length) {
//             return null
//         }
//         val loginData = LoginData()
//
//         // format is xxx://login/server:xxx&user:xxx&password:xxx
//         val data: String = dataString.substring(prefix.length)
//         val values: Array<String> = data.split("&").toTypedArray()
//         if (values.size != PARAMETER_COUNT) {
//             return null
//         }
//         for (value in values) {
//             if (value.startsWith("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
//                 loginData.username = URLDecoder.decode(
//                     value.substring(("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length)
//                 )
//             } else if (value.startsWith("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
//                 loginData.token = URLDecoder.decode(
//                     value.substring(("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length)
//                 )
//             } else if (value.startsWith("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
//                 loginData.serverUrl = URLDecoder.decode(
//                     value.substring(("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length)
//                 )
//             } else {
//                 return null
//             }
//         }
//         return if (!TextUtils.isEmpty(loginData.serverUrl) &&
//             !TextUtils.isEmpty(loginData.username) &&
//             !TextUtils.isEmpty(loginData.token)
//         ) {
//             loginData
//         } else {
//             null
//         }
//     }
//
//     public override fun onDestroy() {
//         super.onDestroy()
//         dispose()
//         // Stop login success monitoring
//         android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacksAndMessages(null)
//     }
//
//     init {
//         sharedApplication!!.componentApplication.inject(this)
//     }
//
//
//
//     companion object {
//         private val TAG = WebViewLoginActivity::class.java.simpleName
//         private const val PROTOCOL_SUFFIX = "://"
//         private const val LOGIN_URL_DATA_KEY_VALUE_SEPARATOR = ":"
//         private const val PARAMETER_COUNT = 3
//     }
// }
