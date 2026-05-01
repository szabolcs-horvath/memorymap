package com.szabolcshorvath.memorymap.util

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

object PerfUtil {
    fun startTrace(traceName: String): Trace {
        return Firebase.performance.newTrace(traceName).also { it.start() }
    }

    suspend fun <R> RoomDatabase.withTransaction(traceName: String, block: suspend () -> R): R {
        return trace(traceName + "_transaction") {
            this.withTransaction(block)
        }
    }

    inline fun <E> trace(traceName: String, block: (Trace) -> E): E {
        val trace = Firebase.performance.newTrace(traceName).also { it.start() }
        return try {
            block(trace)
        } finally {
            trace.stop()
        }
    }

    inline fun <reified T : Any> tracedDao(dao: T): T = tracedDaoInternal(dao, T::class.java)

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> tracedDaoInternal(dao: T, daoClass: Class<T>): T =
        Proxy.newProxyInstance(daoClass.classLoader, arrayOf(daoClass)) { _, method, args ->
            val isSuspend = args?.lastOrNull() is Continuation<*>
            val traceName = "db_${daoClass.simpleName}_${method.name}"

            if (isSuspend) {
                val continuation = args.last() as Continuation<Any?>
                val argsWithoutContinuation = args.dropLast(1).toTypedArray()

                val trace = Firebase.performance.newTrace(traceName).also { it.start() }
                val stopped = AtomicBoolean(false)
                fun stopOnce() {
                    if (stopped.compareAndSet(false, true)) {
                        trace.stop()
                    }
                }

                val tracingContinuation = object : Continuation<Any?> {
                    override val context = continuation.context
                    override fun resumeWith(result: Result<Any?>) {
                        stopOnce()
                        continuation.resumeWith(result)
                    }
                }

                try {
                    val result = method.invoke(dao, *argsWithoutContinuation, tracingContinuation)
                    if (result != COROUTINE_SUSPENDED) {
                        stopOnce()
                    }
                    result
                } catch (e: InvocationTargetException) {
                    stopOnce()
                    throw e.cause ?: e
                } catch (e: Exception) {
                    stopOnce()
                    throw e
                }
            } else {
                val trace = Firebase.performance.newTrace(traceName).also { it.start() }
                try {
                    method.invoke(dao, *(args ?: emptyArray()))
                } catch (e: InvocationTargetException) {
                    throw e.cause ?: e
                } finally {
                    trace.stop()
                }
            }
        } as T
}
