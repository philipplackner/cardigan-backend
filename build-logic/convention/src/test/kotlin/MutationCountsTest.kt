import assertk.assertThat
import assertk.assertions.containsExactly
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

        assertThat(counts).isEqualTo(MutationCounts(total = 100, killed = 56, noCoverage = 20))
        assertThat(counts.survived).isEqualTo(24L)
        assertThat(counts.scorePercent()).isEqualTo("56.0%")
        assertThat(counts.strengthPercent()).isEqualTo("70.0%")
    }

    @Test
    fun `run errors and non-viable mutants count as killed`() {
        val counts = MutationCounts.of(statuses("KILLED" to 1, "RUN_ERROR" to 1, "NON_VIABLE" to 1, "SURVIVED" to 1))

        assertThat(counts).isEqualTo(MutationCounts(total = 4, killed = 3, noCoverage = 0))
    }

    @Test
    fun `describe lists percentages with one decimal and plain counts`() {
        assertThat(MutationCounts(total = 1477, killed = 1010, noCoverage = 272).describe()).isEqualTo(
            "mutation score 68.4%, test strength 83.8% (1010 killed, 195 survived, 272 no coverage, 1477 total)"
        )
    }

    @Test
    fun `percentages round half up`() {
        assertThat(MutationCounts(total = 16, killed = 1, noCoverage = 0).scorePercent()).isEqualTo("6.3%")
        assertThat(MutationCounts(total = 1600, killed = 1001, noCoverage = 0).scorePercent()).isEqualTo("62.6%")
    }

    @Test
    fun `test strength is n-a without covered mutants`() {
        val counts = MutationCounts.of(statuses("NO_COVERAGE" to 3))

        assertThat(counts.describe()).isEqualTo(
            "mutation score 0.0%, test strength n/a (0 killed, 0 survived, 3 no coverage, 3 total)"
        )
    }

    @Test
    fun `summary sums counts for the total and leaves out modules without mutants`() {
        val lines = mutationSummary(
            listOf(
                ":user" to MutationCounts(total = 10, killed = 6, noCoverage = 2),
                ":common" to MutationCounts(total = 0, killed = 0, noCoverage = 0),
                ":board" to MutationCounts(total = 30, killed = 9, noCoverage = 18),
            ),
            "build/reports/pitest/index.html",
        )

        assertThat(lines).containsExactly(
            "Mutation testing (2 modules with mutants):",
            "  :user: mutation score 60.0%, test strength 75.0% (6 killed, 2 survived, 2 no coverage, 10 total)",
            "  :board: mutation score 30.0%, test strength 75.0% (9 killed, 3 survived, 18 no coverage, 30 total)",
            "Total: mutation score 37.5%, test strength 75.0% (15 killed, 5 survived, 20 no coverage, 40 total)",
            "HTML: build/reports/pitest/index.html",
        )
    }

    @Test
    fun `summary header is singular for one module`() {
        val lines = mutationSummary(listOf(":user" to MutationCounts(total = 1, killed = 1, noCoverage = 0)), "r.html")

        assertThat(lines.first()).isEqualTo("Mutation testing (1 module with mutants):")
    }

    @Test
    fun `summary without mutants prints only the no-mutants message`() {
        val lines = mutationSummary(listOf(":user" to MutationCounts(0, 0, 0)), "r.html")

        assertThat(lines).containsExactly("No mutants in the selected modules. No combined report was written.")
    }

    @Test
    fun `min score failure prints a whole threshold as an integer`() {
        val counts = MutationCounts(total = 1477, killed = 1010, noCoverage = 272)

        assertThat(minScoreFailure(counts, BigDecimal("70"))).isEqualTo("Mutation score 68.4% is below mutation.minScore=70")
        assertThat(minScoreFailure(counts, BigDecimal("70.0"))).isEqualTo("Mutation score 68.4% is below mutation.minScore=70")
        assertThat(minScoreFailure(counts, BigDecimal("100"))).isEqualTo("Mutation score 68.4% is below mutation.minScore=100")
    }

    @Test
    fun `min score failure prints a decimal threshold as a plain decimal`() {
        val counts = MutationCounts(total = 1477, killed = 1010, noCoverage = 272)

        assertThat(minScoreFailure(counts, BigDecimal("68.50"))).isEqualTo("Mutation score 68.4% is below mutation.minScore=68.5")
    }

    @Test
    fun `the unrounded score is compared, so a rounded-up score can print as the minimum`() {
        val counts = MutationCounts(total = 10000, killed = 6996, noCoverage = 0)

        assertThat(counts.meets(BigDecimal(70))).isFalse()
        assertThat(counts.meets(BigDecimal("69.96"))).isTrue()
        assertThat(minScoreFailure(counts, BigDecimal(70))).isEqualTo("Mutation score 70.0% is below mutation.minScore=70")
    }

    @Test
    fun `no mutants meet any minimum`() {
        assertThat(MutationCounts.of(emptyList()).meets(BigDecimal(100))).isTrue()
    }
}
