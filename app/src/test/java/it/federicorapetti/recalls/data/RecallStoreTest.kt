package it.federicorapetti.recalls.data

import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.local.isUnread
import it.federicorapetti.recalls.data.model.RecallSource.IT_MINISTRY
import it.federicorapetti.recalls.data.model.RecallSource.IT_OPERATOR
import it.federicorapetti.recalls.data.model.RecallSource.SAFETY_GATE
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecallStoreTest {
    private lateinit var db: RecallDatabase
    private lateinit var store: RecallStore

    private val a = content("SG:a", SAFETY_GATE, 1_000)
    private val b = content("SG:b", SAFETY_GATE, 2_000)
    private val c = content("SG:c", SAFETY_GATE, 3_000)

    @Before
    fun setUp() {
        db = inMemoryDb()
        store = RecallStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `baseline sync is silent`() = runBlocking {
        val newItems = store.store(SAFETY_GATE, listOf(a, b))

        assertTrue(newItems.isEmpty())
        val all = store.observeAll().first()
        assertEquals(2, all.size)
        assertTrue(all.none { it.isUnread })
    }

    @Test
    fun `sync after the baseline returns and marks only new items`() = runBlocking {
        store.store(SAFETY_GATE, listOf(a, b))

        val newItems = store.store(SAFETY_GATE, listOf(a, b, c))

        assertEquals(listOf("SG:c"), newItems.map { it.id })
        assertTrue(newItems.single().isNew)
        val byId = store.observeAll().first().associateBy { it.id }
        assertFalse(byId.getValue("SG:a").isNew)
        assertFalse(byId.getValue("SG:b").isNew)
    }

    @Test
    fun `read state survives a re-sync`() = runBlocking {
        store.store(SAFETY_GATE, listOf(a, b))
        store.store(SAFETY_GATE, listOf(a, b, c))
        store.markRead(c.id)

        store.store(SAFETY_GATE, listOf(a, b, c.copy(title = "changed")))

        val stored = store.observe(c.id).first()!!
        assertEquals("changed", stored.title)
        assertTrue(stored.isRead)
        assertFalse(stored.isUnread)
    }

    @Test
    fun `withdrawn ids are removed`() = runBlocking {
        val x = content("IT:x", IT_OPERATOR, 1_000)
        val y = content("IT:y", IT_OPERATOR, 2_000)
        store.store(IT_OPERATOR, listOf(x, y))

        store.store(IT_OPERATOR, listOf(x), removedIds = listOf(y.id))

        assertEquals(listOf("IT:x"), store.observeAll().first().map { it.id })
    }

    @Test
    fun `etag is kept by recordUnchanged and the sync time advances`() = runBlocking {
        store.store(IT_OPERATOR, emptyList(), etag = "e1")
        assertEquals("e1", store.etag(IT_OPERATOR))

        val t0 = System.currentTimeMillis()
        store.recordUnchanged(IT_OPERATOR)

        assertEquals("e1", store.etag(IT_OPERATOR))
        assertTrue(store.observeLastSync().first()!! >= t0)
    }

    @Test
    fun `recordUnchanged on a never-synced source does nothing`() = runBlocking {
        store.recordUnchanged(IT_MINISTRY)

        assertNull(store.observeLastSync().first())
    }

    @Test
    fun `markAllRead leaves no unread rows`() = runBlocking {
        store.store(SAFETY_GATE, listOf(a, b))
        store.store(SAFETY_GATE, listOf(a, b, c))
        assertEquals(1, store.observeAll().first().count { it.isUnread })

        store.markAllRead()

        assertEquals(0, store.observeAll().first().count { it.isUnread })
    }
}
