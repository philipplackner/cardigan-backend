import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MutationCountsTest {

    private fun statuses(vararg counts: Pair<String, Int>) = counts.flatMap { (status, n) -> List(n) { status } }

    @Test
    fun `timed-out and memory-error mutants count as killed`() {
        val counts = MutationCounts.of(
            statuses("KILLED" to 50, "TIMED_OUT" to 5, "MEMORY_ERROR" to 1, "SURVIVED" to 24, "NO_COVERAGE" to 20)
        )

        assertThat(counts).isEqualTo(MutationCounts(total = 100, killed = 56, covered = 80))
        assertThat(counts.scorePercent()).isEqualTo("56.00")
        assertThat(counts.strengthPercent()).isEqualTo("70.00")
    }

    @Test
    fun `run errors and non-viable mutants are not killed`() {
        val counts = MutationCounts.of(statuses("KILLED" to 1, "RUN_ERROR" to 1, "NON_VIABLE" to 1, "SURVIVED" to 1))

        assertThat(counts).isEqualTo(MutationCounts(total = 4, killed = 1, covered = 4))
    }

    @Test
    fun `no covered mutants gives zero score and strength`() {
        val counts = MutationCounts.of(statuses("NO_COVERAGE" to 3))

        assertThat(counts.scorePercent()).isEqualTo("0.00")
        assertThat(counts.strengthPercent()).isEqualTo("0.00")
    }

    @Test
    fun `a score just below the minimum fails and prints below it`() {
        val counts = MutationCounts(total = 3, killed = 2, covered = 3)

        assertThat(counts.meets(BigDecimal("66.67"))).isFalse()
        assertThat(counts.meets(BigDecimal("66.66"))).isTrue()
        assertThat(counts.scorePercent()).isEqualTo("66.66")
    }

    @Test
    fun `no mutants meet any minimum`() {
        assertThat(MutationCounts.of(emptyList()).meets(BigDecimal(100))).isTrue()
    }
}
