package app.messages.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] and returns its failure instead of throwing. For the boundaries where a
 * telephony provider, radio, or file error must not crash the app.
 *
 * Unlike runCatching, coroutine cancellation still propagates, so a cancelled job stops
 * instead of carrying on, and Errors (out of memory, stack overflow) are not caught.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> attempt(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
