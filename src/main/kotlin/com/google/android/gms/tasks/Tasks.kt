package com.google.android.gms.tasks

import java.util.concurrent.TimeUnit

class Task<T>(internal val result: T?, internal val error: Exception?) {
    val isSuccessful: Boolean get() = error == null
    fun addOnSuccessListener(l: (T) -> Unit): Task<T> { if (error == null) @Suppress("UNCHECKED_CAST") l(result as T); return this }
    fun addOnFailureListener(l: (Exception) -> Unit): Task<T> { error?.let(l); return this }
    fun addOnCompleteListener(l: (Task<T>) -> Unit): Task<T> { l(this); return this }
}

object Tasks {
    fun <T> await(t: Task<T>): T = t.error?.let { throw java.util.concurrent.ExecutionException(it) } ?: t.result as T
    fun <T> await(t: Task<T>, timeout: Long, unit: TimeUnit): T = await(t)
    fun <T> forResult(v: T): Task<T> = Task(v, null)
}
