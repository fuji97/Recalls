package it.federicorapetti.recalls.ui.navigation

import android.content.Intent
import it.federicorapetti.recalls.ui.list.RegionFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LaunchRequestTest {

    private fun roundTrip(request: LaunchRequest): LaunchRequest? {
        val intent = Intent()
        request.writeTo(intent)
        return LaunchRequest.from(intent)
    }

    @Test
    fun `detail round-trips`() {
        assertEquals(LaunchRequest.Detail("SG:1"), roundTrip(LaunchRequest.Detail("SG:1")))
    }

    @Test
    fun `region filters round-trip`() {
        assertEquals(LaunchRequest.Filter(RegionFilter.EU), roundTrip(LaunchRequest.Filter(RegionFilter.EU)))
        assertEquals(LaunchRequest.Filter(RegionFilter.ITALY), roundTrip(LaunchRequest.Filter(RegionFilter.ITALY)))
    }

    @Test
    fun `filter ALL writes nothing`() {
        assertNull(roundTrip(LaunchRequest.Filter(RegionFilter.ALL)))
    }

    @Test
    fun `bare intent decodes to null`() {
        assertNull(LaunchRequest.from(Intent()))
    }

    @Test
    fun `unknown filter value decodes to null`() {
        assertNull(LaunchRequest.from(Intent().putExtra("source_filter", "XX")))
    }

    @Test
    fun `detail wins when both extras exist`() {
        val intent = Intent().putExtra("recall_id", "SG:1").putExtra("source_filter", "EU")

        assertEquals(LaunchRequest.Detail("SG:1"), LaunchRequest.from(intent))
    }

    @Test
    fun `wire format of already-posted notifications still decodes`() {
        assertEquals(
            LaunchRequest.Filter(RegionFilter.ITALY),
            LaunchRequest.from(Intent().putExtra("source_filter", "IT"))
        )
    }
}
