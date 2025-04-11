package org.bibletranslationtools.wat.data

import org.bibletranslationtools.wat.domain.WordStatus

enum class Consensus(val value: String) {
    LIKELY_INCORRECT("Likely Incorrect"),
    LIKELY_CORRECT("Likely Correct"),
    NEEDS_REVIEW("Needs Review"),
    NAME("Name")
}

data class ModelStatus(
    val model: String,
    val status: WordStatus
)

data class ConsensusResult(
    val models: List<ModelStatus>,
    val consensus: Consensus
)

data class SingletonWord(
    val word: String,
    val ref: Verse,
    var result: ConsensusResult? = null
)
