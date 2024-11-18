enum class Clock { REALTIME, MONOTONIC }

class Class(val value: String)

fun main() {
    println(Class("Hello")) // fails with: id from different slab
    println(listOf(1, 2, 3)) // fails with: id from different slab
    println(Clock.entries) // fails with: id from different slab
    println(Clock.REALTIME in Clock.entries) // fails with: id from different slab
    println(0 in Clock.entries.indices) // fails with: assertion failed: index <= Slab::<()>::MAX_CAPACITY
}

// We need it to run WasmEdge with the _initialize function
@WasmExport
fun dummy() {
}
