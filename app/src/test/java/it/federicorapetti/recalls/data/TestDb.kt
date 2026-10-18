package it.federicorapetti.recalls.data

import androidx.room.Room
import it.federicorapetti.recalls.data.local.RecallContent
import it.federicorapetti.recalls.data.local.RecallDatabase
import it.federicorapetti.recalls.data.model.RecallSource
import org.robolectric.RuntimeEnvironment

fun inMemoryDb(): RecallDatabase =
    Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), RecallDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** [id] includes the source prefix, e.g. "SG:1"; remoteId is the part after it. */
fun content(id: String, source: RecallSource, publishedAt: Long, title: String = "t") = RecallContent(
    id = id,
    source = source,
    remoteId = id.substringAfter(':'),
    title = title,
    subtitle = null,
    brand = null,
    reason = null,
    reference = null,
    country = null,
    publishedAt = publishedAt,
    imageUrl = null,
    webUrl = "https://example.test/$id",
    attachmentUrl = null,
    isRevocation = false
)
