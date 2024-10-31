import java.io.BufferedWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.ProcessBuilder

const val PYTHON_PATH = "../venv/bin/python3"

class Guesser(
    private val alphabet: List<String>, // алфавит
    pathToMat: String
) {
    private val matProcess = ProcessBuilder(PYTHON_PATH, pathToMat)
        .redirectErrorStream(true)
        .start()
    private val writer = BufferedWriter(OutputStreamWriter(matProcess.outputStream))
    private val reader = BufferedReader(InputStreamReader(matProcess.inputStream))

    private val S = mutableListOf("") // префиксы
    private val E = mutableListOf("") // суффиксы
    private val table = mutableMapOf<Pair<String, String>, Boolean>().apply {
        put("" to "", isMembership(""))
    }

    fun isMembership(word: String): Boolean {
        writer.apply {
            write("isin\n")
            write("$word\n")
            flush()
        }
        return reader.readLine().trim() == "True"
    }

    fun isEquivalence(): Pair<Boolean, String?> {
        val suffRow = E.joinToString(" ") { if (it.isEmpty()) "e" else it }
        writer.run {
            write("equal\n")
            write("$suffRow\n")

            S.forEach { s ->
                val row = E.joinToString(" ") { e -> if (table.getOrDefault(s to e, false)) "1" else "0" }
                write("${if (s.isEmpty()) "e" else s} $row\n")
            }

            write("end\n")
            flush()
        }

        return reader.readLine().trim().let { result ->
            if (result == "TRUE") true to null else false to result
        }
    }

    fun updateTable() {
        S.forEach { s ->
            E.forEach { e ->
                table.getOrPut(s to e) { isMembership(s + e) }
            }
        }
    }

    fun closeTable() {
        while (true) {
            S.forEach { s ->
                for (a in alphabet) {
                    if ((s + a) !in S) {
                        S.add(s + a)
                        updateTable()
                        return
                    }
                }
            }
            break
        }
    }

    fun buildHypothesis(): Map<String, Any> {
        val states = S.withIndex().associate { it.value to it.index }
        val transitions = mutableMapOf<Pair<Int, String>, Int>()
        S.forEach { s ->
            alphabet.forEach { a ->
                val nextState = s + a
                if (nextState in S) {
                    transitions[states[s]!! to a] = states[nextState]!!
                }
            }
        }
        val acceptStates = S.filter { table[it to ""] == true }.toSet()
        return mapOf("states" to states.values.toSet(), "transitions" to transitions, "accept_states" to acceptStates)
    }

    fun formatTable() {
        val suffRow = E.joinToString("\t") { if (it == "") "e" else it }
        val readableTable = StringBuilder("\t$suffRow\n")
        S.forEach { s ->
            val row = E.map { e -> table.getOrDefault(s to e, false).let { if (it) "1" else "0" } }
            val sLabel = if (s == "") "e" else s
            readableTable.append("$sLabel\t${row.joinToString("\t")}\n")
        }
        println(readableTable.toString())
    }

    fun run() {
        while (true) {
            closeTable()
            val (equivalent, counterexample) = isEquivalence()
            if (equivalent) {
                println("Гипотеза верна! Автомат построен.")
                formatTable()
                break
            } else {
                println("Получен контрпример: $counterexample")
                counterexample?.let {
                    for (i in 1..it.length) {
                        val prefix = it.substring(0, i)
                        if (prefix !in S) S.add(prefix)
                    }
                    for (i in it.indices) {
                        val suffix = it.substring(i)
                        if (suffix !in E) E.add(suffix)
                    }
                    updateTable()
                }
            }
        }
    }

    fun close() {
        matProcess.destroy()
    }
}


