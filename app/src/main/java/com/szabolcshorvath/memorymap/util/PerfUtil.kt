package com.szabolcshorvath.memorymap.util

import com.google.firebase.Firebase
import com.google.firebase.perf.metrics.Trace
import com.google.firebase.perf.performance
import java.lang.reflect.Proxy

object PerfUtil {
    fun startTrace(traceName: String): Trace {
        return Firebase.performance.newTrace(traceName).also { it.start() }
    }

    inline fun <E> trace(traceName: String, block: (Trace) -> E): E {
        val trace = Firebase.performance.newTrace(traceName).also { it.start() }
        return try {
            block(trace)
        } finally {
            trace.stop()
        }
    }

    inline fun <reified T : Any> tracedDao(dao: T): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java)
    ) { _, method, args ->
        val trace = Firebase.performance.newTrace("db_${T::class.simpleName}_${method.name}").also { it.start() }
        try {
            if (args != null) {
                method.invoke(dao, *args)
            } else {
                method.invoke(dao)
            }
        } finally {
            trace.stop()
        }
    } as T
}
