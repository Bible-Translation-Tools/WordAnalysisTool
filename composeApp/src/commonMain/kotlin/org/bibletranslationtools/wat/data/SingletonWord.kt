package org.bibletranslationtools.wat.data

import org.bibletranslationtools.wat.domain.WordStatus

enum class Consensus {
    LIKELY_INCORRECT,
    LIKELY_CORRECT,
    NEEDS_REVIEW,
    NAME
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
