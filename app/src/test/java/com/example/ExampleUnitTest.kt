package com.example

import com.example.data.repository.SalonRepository
import org.junit.Assert.*
import org.junit.Test
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSlotFormatting_UTC_to_IST() {
    val repo = SalonRepository(ApplicationProvider.getApplicationContext())

    // 04:30:00 UTC = 10:00 AM IST
    val slotMorning = repo.formatSlotDisplay("2026-09-25T04:30:00+00:00")
    assertEquals("10:00 AM", slotMorning)
    assertEquals("Morning", repo.calculatePeriod(slotMorning))

    // 06:30:00 UTC = 12:00 PM IST
    val slotNoon = repo.formatSlotDisplay("2026-09-25T06:30:00+00:00")
    assertEquals("12:00 PM", slotNoon)
    assertEquals("Afternoon", repo.calculatePeriod(slotNoon))

    // 14:00:00 UTC = 07:30 PM IST
    val slotEvening = repo.formatSlotDisplay("2026-09-25T14:00:00+00:00")
    assertEquals("07:30 PM", slotEvening)
    assertEquals("Evening", repo.calculatePeriod(slotEvening))
  }

  @Test
  fun testPlainTimeFormatting() {
    val repo = SalonRepository(ApplicationProvider.getApplicationContext())
    assertEquals("10:00 AM", repo.formatSlotDisplay("10:00:00"))
    assertEquals("08:00 PM", repo.formatSlotDisplay("20:00:00"))
  }
}

