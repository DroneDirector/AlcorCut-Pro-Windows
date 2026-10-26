package com.google.android.gms.tasks

import java.util.concurrent.TimeUnit

class Task<T>(internal val result: T?, internal val error: Exception?)

object Tasks {
    fun <T> await(t: Task<T>): T = t.error?.let { throw java.util.concurrent.ExecutionException(it) } ?: t.result as T
    fun <T> await(t: Task<T>, timeout: Long, unit: TimeUnit): T = await(t)
    fun <T> forResult(v: T): Task<T> = Task(v, null)
}
