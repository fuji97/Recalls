package it.federicorapetti.recalls.data.remote.safetygate

/** Upper bound on concurrent Safety Gate search requests within one sync. */
const val SG_MAX_PARALLEL_REQUESTS = 6
private const val SG_MIN_PAGE_SIZE = 100
private const val SG_MAX_PAGE_SIZE = 200
private const val SG_MAX_PAGES = 30

data class SgPagePlan(val pageSize: Int, val pageCount: Int)

/**
 * Splits [totalElements] search hits so a typical sync needs one round of
 * [SG_MAX_PARALLEL_REQUESTS] parallel requests. The server spends ~0.1 s per returned item,
 * so pages are capped at [SG_MAX_PAGE_SIZE] (~20 s) to stay well inside the 60 s read timeout.
 */
fun sgPagePlan(totalElements: Int): SgPagePlan {
    if (totalElements <= 0) return SgPagePlan(pageSize = SG_MIN_PAGE_SIZE, pageCount = 0)
    val pageSize = ((totalElements + SG_MAX_PARALLEL_REQUESTS - 1) / SG_MAX_PARALLEL_REQUESTS)
        .coerceIn(SG_MIN_PAGE_SIZE, SG_MAX_PAGE_SIZE)
    val pageCount = ((totalElements + pageSize - 1) / pageSize).coerceAtMost(SG_MAX_PAGES)
    return SgPagePlan(pageSize, pageCount)
}
