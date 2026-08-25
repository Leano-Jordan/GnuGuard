package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.CurrentNetworkInfo
import com.example.data.model.WifiNetwork
import com.example.data.model.WifiSecurityType
import com.example.ui.screens.ConnectedWifiHeroCard
import com.example.ui.theme.GnuGuardTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      GnuGuardTheme {
        ConnectedWifiHeroCard(
          networkInfo = CurrentNetworkInfo(
            isConnectedToWifi = true,
            ssid = "GnuGuard-Secure-5G",
            localIpAddress = "192.168.1.105",
            linkSpeedMbps = 866,
            frequencyMhz = 5180,
            rssiDbm = -45
          ),
          onInspect = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
