package com.eltnegcellist.emma

/** Main-thread session identity: late asynchronous results must not revive a stopped session. */
class SessionGate {
    @Volatile private var generation = 0L
    private var active = false
    fun start(): Long { generation++; active = true; return generation }
    fun stop() { generation++; active = false }
    fun ticket(): Long = generation
    fun accepts(ticket: Long): Boolean = active && ticket == generation
}

