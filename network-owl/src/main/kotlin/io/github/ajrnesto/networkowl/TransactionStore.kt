package io.github.ajrnesto.networkowl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

internal class TransactionStore(private val capacity: () -> Int) {
    private val nextId = AtomicLong()
    private val _transactions = MutableStateFlow<List<HttpTransaction>>(emptyList())

    val transactions: StateFlow<List<HttpTransaction>> = _transactions.asStateFlow()

    @Volatile
    var onChange: (() -> Unit)? = null

    fun add(create: (id: Long) -> HttpTransaction): Long {
        val transaction = create(nextId.incrementAndGet())
        _transactions.update { (listOf(transaction) + it).take(capacity()) }
        onChange?.invoke()
        return transaction.id
    }

    fun update(id: Long, transform: (HttpTransaction) -> HttpTransaction) {
        _transactions.update { list -> list.map { if (it.id == id) transform(it) else it } }
        onChange?.invoke()
    }

    fun clear() {
        _transactions.value = emptyList()
        onChange?.invoke()
    }
}
