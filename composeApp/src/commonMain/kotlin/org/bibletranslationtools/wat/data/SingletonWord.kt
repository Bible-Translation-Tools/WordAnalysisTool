package org.bibletranslationtools.wat.data

enum class Consensus {
    MISSPELLING,
    PROPER_NAME,
    SOMETHING_ELSE,
    UNDEFINED;

    companion object {
        fun of(answer: String): Consensus {
            return when {
                answer.contains("proper name") -> PROPER_NAME
                answer.contains("proper noun") -> PROPER_NAME
                answer.contains("misspell") -> MISSPELLING
                answer.contains("typo") -> MISSPELLING
                answer.contains("something else") -> SOMETHING_ELSE
                else -> UNDEFINED
            }
        }
    }
}

data class ModelConsensus(
    val model: String,
    val consensus: Consensus
)

data class ConsensusResult(
    val models: List<ModelConsensus>,
    val consensus: Consensus
)

data class SingletonWord(
    val count: Int,
    val ref: Verse,
    var result: ConsensusResult? = null
)
