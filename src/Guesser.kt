import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.ProcessBuilder

const val PYTHON_PATH = "../venv/bin/python3"
const val EXPORTED_TABLE_PATH = "./result.xlsx"

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

    fun isEquivalent(): Pair<Boolean, String?> {
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

    // обновляет таблицу классов эквивалентности
    fun updateTable() {
        S.forEach { s ->
            E.forEach { e ->
                table.getOrPut(s to e) { isMembership(s + e) }
            }
        }
    }

    // расширяет множество префиксов S
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

    fun run() {
        while (true) {
            // заполняем таблицу первоначальными суффиксами и префиксами
            closeTable()

            val (equivalent, counterexample) = isEquivalent()
            if (equivalent) {
                println("Гипотеза верна! Автомат построен.")
                printTable()
                exportToExcel(EXPORTED_TABLE_PATH)
                break
            } else {
                println("Контрпример от МАТ-а: $counterexample")
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

    fun printTable() {
        val suffRow = E.joinToString("\t") { if (it == "") "e" else it }
        val readableTable = StringBuilder("\t$suffRow\n")
        S.forEach { s ->
            val row = E.map { e -> table.getOrDefault(s to e, false).let { if (it) "1" else "0" } }
            val sLabel = if (s == "") "e" else s
            readableTable.append("$sLabel\t${row.joinToString("\t")}\n")
        }
        println(readableTable.toString())
    }

    fun exportToExcel(filePath: String) {
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Equivalence Table")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Prefixes \\ Suffixes")
                E.forEachIndexed { index, e -> createCell(index + 1).setCellValue(e.ifEmpty { "e" }) }
            }
            S.forEachIndexed { rowIndex, s ->
                val row = sheet.createRow(rowIndex + 1)
                row.createCell(0).setCellValue(s.ifEmpty { "e" })
                E.forEachIndexed { colIndex, e -> row.createCell(colIndex + 1).setCellValue(if (table[s to e] == true) "1" else "0") }
            }
            FileOutputStream(File(filePath)).use { workbook.write(it) }
            println("Таблица успешно сохранена в $filePath")
        }
    }

    fun close() {
        matProcess.destroy()
    }
}