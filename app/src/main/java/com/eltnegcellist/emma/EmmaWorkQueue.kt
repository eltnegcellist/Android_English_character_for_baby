package com.eltnegcellist.emma

import java.util.concurrent.Executors

/** Serialize native model work across Activity instances, including recreation. */
internal object EmmaWorkQueue {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "EmmaModelWorker").apply { isDaemon = true }
    }
    fun execute(task: () -> Unit) { executor.execute(task) }
}

