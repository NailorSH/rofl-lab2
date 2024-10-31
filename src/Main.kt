const val MAT_PATH = "../MAT/tfl-lab2/main.py"

fun main() {
    val mazeAlphabet = listOf("S", "N", "W", "E")
    val guesser = Guesser(mazeAlphabet, MAT_PATH)
    guesser.run()
    guesser.close()
}