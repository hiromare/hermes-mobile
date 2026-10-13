package com.m57.hermescontrol.data.update

import com.m57.hermescontrol.data.remote.TlsTestContext
import com.m57.hermescontrol.data.remote.TlsTestIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Endpoint contract for the opt-in release-candidate channel: the stable path
 * must keep using `releases/latest` (GitHub excludes pre-releases there), and
 * only the opt-in path may scan the releases list.
 */
class AppUpdateCheckerNetworkTest {
    private val serverIdentity = TlsTestIdentity("server")
    private val serverTls = TlsTestContext(serverIdentity)
    private val clientTrust = TlsTestContext(trusted = listOf(serverIdentity.certificate))
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker(): AppUpdateChecker =
        AppUpdateChecker(
            client = OkHttpClient(),
            apiBaseUrl = server.url("/repos/Hy4ri/hermes-mobile").toString(),
        )

    private fun httpsServer(): MockWebServer =
        MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory(), false)
            start()
        }

    private fun httpsClient(followRedirects: Boolean = true): OkHttpClient =
        OkHttpClient
            .Builder()
            .sslSocketFactory(clientTrust.sslSocketFactory(), clientTrust.trustManager)
            .hostnameVerifier { host, session -> OkHttpClient().hostnameVerifier.verify(host, session) }
            .followRedirects(followRedirects)
            .build()

    private fun enqueue(
        body: String,
        code: Int = 200,
        headers: Map<String, String> = emptyMap(),
    ) {
        val response = MockResponse().setResponseCode(code).setBody(body)
        headers.forEach(response::addHeader)
        server.enqueue(response)
    }

    @Test
    fun fetchLatestRelease_defaultUsesStableLatestEndpoint() =
        runTest {
            enqueue(STABLE_BODY)

            val info = checker().fetchLatestRelease()

            assertEquals("v1.24.2", info?.tagName)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases/latest", server.takeRequest().path)
        }

    @Test
    fun fetchLatestRelease_releaseCandidatesScanTheReleasesList() =
        runTest {
            enqueue(STABLE_BODY)
            enqueue(RELEASES_BODY)

            val info = checker().fetchLatestRelease(includeReleaseCandidates = true)

            assertEquals("v1.25.0-rc.3", info?.tagName)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases/latest", server.takeRequest().path)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases?per_page=100&page=1", server.takeRequest().path)
        }

    @Test
    fun fetchLatestRelease_stableFallsBackToReleasesWhenLatestIsNotUsable() =
        runTest {
            enqueue("{}")
            enqueue(RELEASES_BODY)

            val info = checker().fetchLatestRelease()

            assertEquals("v1.24.2", info?.tagName)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases/latest", server.takeRequest().path)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases?per_page=100&page=1", server.takeRequest().path)
        }

    @Test
    fun fetchLatestRelease_rcScanFollowsGithubPagination() =
        runTest {
            enqueue(STABLE_BODY)
            val nextPage = server.url("/repos/Hy4ri/hermes-mobile/releases?per_page=100&page=2")
            enqueue(RELEASES_BODY, headers = mapOf("Link" to "<$nextPage>; rel=\"next\""))
            enqueue(
                """[{"tag_name":"v1.26.0-rc.1","prerelease":true,"assets":[{"name":"release.apk","size":5,"browser_download_url":"https://example.com/new.apk"}]}]""",
            )

            val info = checker().fetchLatestRelease(includeReleaseCandidates = true)

            assertEquals("v1.26.0-rc.1", info?.tagName)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases/latest", server.takeRequest().path)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases?per_page=100&page=1", server.takeRequest().path)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases?per_page=100&page=2", server.takeRequest().path)
        }

    @Test
    fun fetchLatestRelease_rcScanAcceptsRepositoriesPaginationFormat() =
        runTest {
            enqueue(STABLE_BODY)
            val nextPage = server.url("/repositories/1270568813/releases?per_page=100&page=2")
            enqueue(RELEASES_BODY, headers = mapOf("Link" to "<$nextPage>; rel=\"next\""))
            enqueue(
                """[{"tag_name":"v2.0.0-rc.1","prerelease":true,"assets":[{"name":"release.apk","size":5,"browser_download_url":"https://example.com/new.apk"}]}]""",
            )

            val info = checker().fetchLatestRelease(includeReleaseCandidates = true)

            assertEquals("v2.0.0-rc.1", info?.tagName)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases/latest", server.takeRequest().path)
            assertEquals("/repos/Hy4ri/hermes-mobile/releases?per_page=100&page=1", server.takeRequest().path)
            assertEquals("/repositories/1270568813/releases?per_page=100&page=2", server.takeRequest().path)
        }

    @Test
    fun fetchLatestRelease_releaseCandidatesStillIgnoreAlphaAndBetaButKeepStable() =
        runTest {
            enqueue(STABLE_BODY)
            enqueue(ALPHA_BETA_BODY)

            assertEquals("v1.24.2", checker().fetchLatestRelease(includeReleaseCandidates = true)?.tagName)
        }

    @Test
    fun fetchLatestRelease_nonSuccessYieldsNull() =
        runTest {
            // Each channel checks the stable endpoint and falls back to the list on 404.
            enqueue("", code = 404)
            enqueue("", code = 404)
            enqueue("", code = 404)
            enqueue("", code = 404)

            assertNull(checker().fetchLatestRelease())
            assertNull(checker().fetchLatestRelease(includeReleaseCandidates = true))
        }

    @Test
    fun downloadApk_cancellingCoroutineCancelsNetworkAndDeletesPartialFile() =
        runTest {
            val httpsServer = httpsServer()
            httpsServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val destination = File.createTempFile("hermes-update", ".apk")
            destination.writeText("partial")
            val download =
                launch(Dispatchers.IO) {
                    AppUpdateChecker(client = httpsClient())
                        .downloadApk(httpsServer.url("/slow.apk").toString(), destination) {}
                }

            assertEquals("/slow.apk", httpsServer.takeRequest(5, TimeUnit.SECONDS)?.path)
            download.cancelAndJoin()
            httpsServer.shutdown()

            assertFalse(destination.exists())
        }

    @Test
    fun downloadApk_invalidUrlsReturnFailureAndDeleteDestination() =
        runTest {
            val destination = File.createTempFile("hermes-update", ".apk")
            destination.writeText("partial")

            val downloaded = checker().downloadApk("file:///tmp/release.apk", destination) {}

            assertFalse(downloaded)
            assertFalse(destination.exists())

            val malformedDestination = File.createTempFile("hermes-update", ".apk")
            malformedDestination.writeText("partial")

            val malformedDownloaded = checker().downloadApk("https://bad host/release.apk", malformedDestination) {}

            assertFalse(malformedDownloaded)
            assertFalse(malformedDestination.exists())
        }

    @Test
    fun downloadApk_httpUrlReturnsFailureAndDeletesDestination() =
        runTest {
            val destination = File.createTempFile("hermes-update", ".apk")
            destination.writeText("partial")

            val downloaded = checker().downloadApk(server.url("/release.apk").toString(), destination) {}

            assertFalse(downloaded)
            assertFalse(destination.exists())
            assertEquals(0, server.requestCount)
        }

    @Test
    fun downloadApk_httpsToHttpsRedirectSucceeds() =
        runTest {
            val first = httpsServer()
            val second = httpsServer()
            val destination = File.createTempFile("hermes-update", ".apk")
            try {
                first.enqueue(
                    MockResponse()
                        .setResponseCode(302)
                        .addHeader("Location", second.url("/signed.apk")),
                )
                second.enqueue(MockResponse().setBody("signed APK bytes"))
                val downloaded =
                    AppUpdateChecker(client = httpsClient(followRedirects = true))
                        .downloadApk(first.url("/release.apk").toString(), destination) {}
                assertEquals(true, downloaded)
                assertEquals("signed APK bytes", destination.readText())
                assertEquals("/release.apk", first.takeRequest().path)
                assertEquals("/signed.apk", second.takeRequest().path)
            } finally {
                destination.delete()
                first.shutdown()
                second.shutdown()
            }
        }

    @Test
    fun downloadApk_httpsToHttpRedirectReturnsFailureAndDeletesDestination() =
        runTest {
            val httpsServer = httpsServer()
            val destination = File.createTempFile("hermes-update", ".apk")
            destination.writeText("partial")
            httpsServer.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", server.url("/downgrade.apk")),
            )

            val downloaded =
                AppUpdateChecker(client = httpsClient(followRedirects = true))
                    .downloadApk(httpsServer.url("/release.apk").toString(), destination) {}

            assertFalse(downloaded)
            assertFalse(destination.exists())
            assertEquals("/release.apk", httpsServer.takeRequest().path)
            // Even with a caller-supplied followRedirects=true client,
            // the insecure HTTP target must NEVER receive a request.
            assertEquals(0, server.requestCount)
            httpsServer.shutdown()
        }

    private companion object {
        const val STABLE_BODY = """
{
  "tag_name": "v1.24.2",
  "prerelease": false,
  "draft": false,
  "assets": [
    {
      "name": "hermes-mobile-v1.24.2.apk",
      "size": 12345,
      "browser_download_url": "https://example.com/stable.apk"
    }
  ]
}
"""

        const val RELEASES_BODY = """
[
  {
    "tag_name": "v1.25.0-rc.3",
    "prerelease": true,
    "draft": false,
    "assets": [
      {
        "name": "hermes-mobile-v1.25.0-rc.3.apk",
        "size": 999,
        "browser_download_url": "https://example.com/rc.apk"
      }
    ]
  },
  {
    "tag_name": "v1.24.2",
    "prerelease": false,
    "draft": false,
    "assets": [
      {
        "name": "hermes-mobile-v1.24.2.apk",
        "size": 12345,
        "browser_download_url": "https://example.com/stable.apk"
      }
    ]
  }
]
"""

        const val ALPHA_BETA_BODY = """
[
  {
    "tag_name": "v1.30.0-alpha.1",
    "prerelease": true,
    "assets": [
      {
        "name": "hermes-mobile-v1.30.0-alpha.1.apk",
        "size": 1,
        "browser_download_url": "https://example.com/alpha.apk"
      }
    ]
  },
  {
    "tag_name": "v1.29.0-beta.4",
    "prerelease": true,
    "assets": [
      {
        "name": "hermes-mobile-v1.29.0-beta.4.apk",
        "size": 1,
        "browser_download_url": "https://example.com/beta.apk"
      }
    ]
  }
]
"""
    }
}
