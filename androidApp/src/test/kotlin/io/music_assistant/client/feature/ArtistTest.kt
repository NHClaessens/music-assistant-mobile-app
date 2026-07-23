package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.assertMediaDisplayed
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.home.HomeScreenSemantics
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config
import kotlin.getValue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class ArtistTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `show artist albums`() {
        val artist = ServerMediaItemFixtures.artist()
        val album = ServerMediaItemFixtures.album(artist = artist)
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickOnMedia(artist, withinTag = HomeScreenSemantics.rowTag("recently_added_artists"))
            .assertMediaDisplayed(album)
    }
}
