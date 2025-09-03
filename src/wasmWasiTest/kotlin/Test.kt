import kotlin.test.Test
import kotlin.test.assertTrue

class WasiTest {
    @Test
    fun mainTest() {
        val monotonicTime1 = wasiMonotonicTime()
        val monotonicTime2 = wasiMonotonicTime()
        assertTrue(monotonicTime1 <= monotonicTime2, "Wasi monotonic clock is not monotonic :(")
    }
}