package data

import com.gyanoba.inspektor.data.entites.HttpTransaction
import data.db.createDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant


internal class InspektorDataSource private constructor() {
    private val db = createDatabase()

    suspend fun insertHttpTransaction(httpTransaction: HttpTransaction) =
        withContext(Dispatchers.IO) {
            db.transactionWithResult {
                db.httpTransactionQueries.insert(httpTransaction)
                db.httpTransactionQueries.lastInsertRowId().executeAsOne()
            }
        }

    suspend fun updateHttpTransaction(httpTransaction: HttpTransaction) =
        withContext(Dispatchers.IO) {
            db.httpTransactionQueries.update(
                httpTransaction.responseCode,
                httpTransaction.responseBody,
                httpTransaction.responsePayloadSize,
                httpTransaction.responseContentType,
                httpTransaction.responseHeaders,
                httpTransaction.responseHeadersSize,
                httpTransaction.responseBody,
                httpTransaction.isResponseBodyEncoded,
                httpTransaction.id,
            )
        }

    suspend fun getAllLatestHttpTransactions() = withContext(Dispatchers.IO) {
        db.httpTransactionQueries.getAllLatest().executeAsList()
    }

    suspend fun deleteBefore(timestamp: Instant) = withContext(Dispatchers.IO) {
        db.httpTransactionQueries.deleteBefore(timestamp)
    }

    companion object {
        val Instance by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) { InspektorDataSource() }
    }
}