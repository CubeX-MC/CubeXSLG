package io.github.adlamb.cubex.database

interface TransactionRunner {
    suspend fun <T> inTransaction(block: () -> T): T
}
