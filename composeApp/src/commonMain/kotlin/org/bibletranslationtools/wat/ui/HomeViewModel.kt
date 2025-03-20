package org.bibletranslationtools.wat.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bibletranslationtools.wat.data.ContentInfo
import org.bibletranslationtools.wat.data.LanguageInfo
import org.bibletranslationtools.wat.data.Progress
import org.bibletranslationtools.wat.data.Verse
import org.bibletranslationtools.wat.domain.BielGraphQlApi
import org.bibletranslationtools.wat.domain.DownloadUsfm
import org.bibletranslationtools.wat.domain.UsfmBookSource
import org.bibletranslationtools.wat.http.onError
import org.bibletranslationtools.wat.http.onSuccess
import org.jetbrains.compose.resources.getString
import wordanalysistool.composeapp.generated.resources.Res
import wordanalysistool.composeapp.generated.resources.downloading_usfm
import wordanalysistool.composeapp.generated.resources.fetching_heart_languages
import wordanalysistool.composeapp.generated.resources.fetching_resource_types
import wordanalysistool.composeapp.generated.resources.preparing_for_analysis
import wordanalysistool.composeapp.generated.resources.unknown_error

class HomeViewModel(
    private val bielGraphQlApi: BielGraphQlApi,
    private val downloadUsfm: DownloadUsfm,
    private val usfmBookSource: UsfmBookSource
) : ScreenModel {

    var error by mutableStateOf<String?>(null)
        private set
    var progress by mutableStateOf<Progress?>(null)
        private set

    private val _verses = MutableStateFlow<List<Verse>>(emptyList())
    val verses = _verses.asStateFlow()

    private val _heartLanguages = MutableStateFlow<List<LanguageInfo>>(emptyList())
    val heartLanguages = _heartLanguages
        .onStart { fetchHeartLanguages() }
        .stateIn(
            screenModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    fun fetchHeartLanguages() {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.fetching_heart_languages))
            // TODO Remove debug code
            val en = LanguageInfo("en", "English", "English")
            val ru = LanguageInfo("ru", "Русский", "Russian")
            _heartLanguages.value = bielGraphQlApi.getHeartLanguages() + en + ru
            progress = null
        }
    }

    suspend fun fetchResourceTypesForHeartLanguage(
        ietfCode: String
    ): List<String> {
        progress = Progress(0f, getString(Res.string.fetching_resource_types))
        // TODO Remove debug code
        val resourceTypes = if (ietfCode in listOf("en","ru")) {
            listOf("ulb")
        } else {
            bielGraphQlApi.getUsfmForHeartLanguage(ietfCode).keys.toList()
        }
        progress = null
        return resourceTypes
    }

    fun fetchUsfmForHeartLanguage(
        ietfCode: String,
        resourceType: String
    ) {
        screenModelScope.launch {
            progress = Progress(0f, getString(Res.string.downloading_usfm))

            // TODO Remove debug code
            val books = when(ietfCode) {
                "en" -> listOf(ContentInfo("", "Jude", "jud", null))
                "ru" ->listOf(ContentInfo("", "Послание Иуды", "jud", null))
                else -> bielGraphQlApi.getBooksForTranslation(ietfCode, resourceType)
            }

            val totalBooks = books.size
            val allVerses = mutableListOf<Verse>()

            withContext(Dispatchers.Default) {
                books.forEachIndexed { index, book ->
                    book.url?.let { url ->
                        val currentProgress = (index+1)/totalBooks.toFloat()

                        // TODO Remove debug code
                        if (ietfCode !in listOf("en","ru")) {
                            val response = downloadUsfm(url)

                            response.onSuccess { bytes ->
                                allVerses.addAll(usfmBookSource.parse(bytes.decodeToString()))
                            }.onError { err ->
                                error = err.description ?: getString(Res.string.unknown_error)
                                allVerses.clear()
                                return@withContext
                            }
                        } else {
                            if (ietfCode == "en") {
                                allVerses.addAll(getEnglishFakeVerses())
                            } else {
                                allVerses.addAll(getRussianFakeVerses())
                            }
                        }

                        progress = Progress(
                            currentProgress,
                            getString(Res.string.downloading_usfm)
                        )
                    }
                }
            }

            _verses.value = allVerses

            progress = Progress(0f, getString(Res.string.preparing_for_analysis))
            delay(1000)
            progress = null
        }
    }

    // TODO Remove debug code
    suspend fun getEnglishFakeVerses(): List<Verse> {
        val usfm = """
        \id JUD Unlocked Literal Bible
        \ide UTF-8
        \h Jude
        \toc1 The Letter of Jude
        \toc2 Jude
        \toc3 Jud
        \mt Jude

        \s5
        \c 1
        \p
        \v 1 Jude, a servant of Jesus Christ and brother of Jamess, to those who are called, beloved in God the Father, and kept for Jesus Christ:
        \p
        \v 2 May mercy and peace and love be multiplied to you.

        \s5
        \p
        \v 3 Beloved, while I was making every efort to write to you about our common salvation, I had to write to you to exhort you to struggle earnestly for the faith that was entrusted once for all to God's holy people.
        \v 4 For certain men have slipped in secretly among you. These men were marked out for condemnation. They are ungodly men who have changed the grace of our God into sensuality, and who deny our only Master and Lord, Jesus Christ.

        \s5
        \p
        \v 5 Now I wish to remind you—although once you fully knew it—that the Lord saved a people out of the land of Egypt, but that afterward he destroyed those who did not believe.
        \v 6 Also, angels who did not keep to their own position of authority, but who left their proper dwelling place—God has kept them in everlasting chains, in utter darkness, for the judgment on the great day.

        \s5
        \v 7 So also Sodom and Gomorrah and the cities around them gave themselves over to sexual immorality and perverse sexual acts. They serve as an example of those who suffer the punishment of eternal fire.
        \v 8 Yet in the same way, these dreamers also defile their bodies. They reject authority and they slander the glorious ones.

        \s5
        \v 9 But even Michael the archangel, when he was arguing with the devil and disputing with him about the body of Moses, did not dare to bring a slanderous judgment against him, but he said, "May the Lord rebuke you!"
        \v 10 But these people insult whatever they do not understand; and what they do understand naturally, like unreasoning animals, these are the very things that destroy them.
        \v 11 Woe to them! For they have walked in the way of Cain and have plunged into Balam's error for profit. They have perished in Korash's rebellion.

        \s5
        \v 12 These people are dangerous reefs at your love feasts, feasting with you fearlessly—shepherds who only feed themselves. They are clouds without rain, carried along by winds; autumn trees without fruit—twice dead, uprooted.
        \v 13 They are violent waves in the sea, foaming up their shame; wandering stars, for whom the gloom of complete darkness has been reserved forever.

        \s5
        \v 14 Enoch, the seventh from Adam, prephesied about them, saying, "Look! The Lord is coming with thousands and thousands of his holy ones.
        \v 15 He is coming to execute judgment on everyone. He is coming to convict all the ungodly of all the works they have done in an ungodly way, and of all the bitter words that ungodly sinners have spoken against him."
        \v 16 These are grumblers, complainers, following their evil desires. Their mouths speak loud boasts, flattering others for profit.

        \s5
        \p
        \v 17 But you, beloved, remember hte words that were spoken in the past by the apostles of our Lord Jesus Christ.
        \v 18 They said to yuo, "In the last time there will be mockers who will follow their own ungodly desires."
        \v 19 It is these who cause divisions; they are worldly, and they do not have the Spirit.

        \s5
        \v 20 But you, beloved, build yourselves up in your most holy faith, and pray in the Holy Spirit.
        \v 21 Keep yourselves in God's love, and wait for the mercy of our Lord Jesus Christ that brings you eternal life.

        \s5
        \v 22 Be merciful to those who doubt.
        \v 23 Save others by snatching them out of teh fire; to others show mercy with fear, hating even the garment defiled by the flesh.

        \s5
        \p
        \v 24 Now to the one who is able to keep you from stumbling and to cause you to stand before his glorious presence without blemish and with great joy,
        \v 25 to the only God our Savior through Jesus Christ our Lord, be glory, majesty, dominion, and authority, before all time, now, and forever. Amen.
        """.trimIndent()
        return usfmBookSource.parse(usfm)
    }

    // TODO Remove debug code
    suspend fun getRussianFakeVerses(): List<Verse> {
        val usfm = """
        \id JUD
        \ide UTF-8
        \h Иуды
        \toc1 Иуды
        \toc2 Иуды
        \toc3 jud
        \mt Иуды
        
        \s5
        \c 1
        \p 
        \v 1 Иуда, раб Иисуса Христа, брат Иакова, призванным, которые освящены Богом Отцом и сохранены Иисусом Христом: 
        \v 2 милость для вас, мир и любовь пусть умножатся.
        
        \s5
        \v 3 Возлюбленные! Имея всё усердие писать вам об общем спасении, я счёл нужным написать вам наставление: сражаться за веру, однажды переданную святым. 
        \v 4 Потому что вкрадываются некоторые люди, прежде предназначенные к осуждению, нечестивые, обращающие благодать Бога нашего в повод к разврату и отвергающие единого Правителя Бога и Господа нашего Иисуса Христа.
        
        \s5
        \v 5 Я хочу напомнить вам, знающим это, что Господь, избавив народ из египетской земли, затем погубил неверовавших 
        \v 6 и ангелов, которые не сохранили достоинства, но покинули своё жилище, сохраняет в вечных оковах, во тьме, на суд великого дня.
        
        \s5
        \v 7 Как Содон и Гомора, приведённые в пример, и окрестные города, подобно им, предавались разврату, блуду, ходили за другой плотью и подверглись наказанию в огне на веки – 
        \v 8 так точно будет и с этими метателями, которые оскверняют плоть, отвергают госпотство и бесчестят славу.
        
        \s5
        \v 9 Михаил Архангел, когда спорил с дьяволом о теле Моисея, не осмелился вынести осуждающего приговора, но сказал: «Пусть запретит тебе Господь». 
        \v 10 А эти злословят то, чего не знают. Что же знают по природе своей, как неразумные животные - этим уничтожают себя. 
        \v 11 Горе им, потому что идут путем Каина, идут за плату, заблуждаясь как Валаам, и в раздоре погибают, как Корей.
        
        \s5
        \v 12 Они и бывают соблазном на ваших вечерях любви. Обедая с вами, без страха откармливают себя. Они как безводные облака, носимые ветром, как осенние деревья - бесплодные, дважды умершие, вырванные с корнем, 
        \v 13 как свирепые морские волны, пенящиеся своим позором, как скитающиеся звезды, для которых сохраняется мрак тьмы навеки.
        
        \s5
        \v 14 О них произнёс пророчество и Енох, седьмой от Алама, говоря: «Вот, идёт Господь с десятью тысячами святых Его 
        \v 15 произвести суд над всеми и обличить всех нечестивых между ними, во всех делах, в которых они поступали нечестиво, и во всех жестоких словах, которые произносили на Него нечестивые грешники». 
        \v 16 Они ничем не довольные ворчуны, поступающие по своим прихотям. Открывая свой рот надменно, льстят для своей выгоды.
        
        \s5
        \v 17 Но вы, возлюбленные, помните слова прежде сказанные через Апостолов Господа нашего Иисуса Христа. 
        \v 18 Они говорили вам, что в последнее время появятся насмешники, поступающие по своим греховным желаниям. 
        \v 19 Они, отделяюшие себя [от единства веры], душевные, духа не имеющие.
        
        \s5
        \v 20 А вы, возлюбленные, утверждая себя на святейшей вере вашей, молясь в Духе Святом, 
        \v 21 храните себя в любви Божьей, ожидая милость Господа нашего Иисуса Христа для вечной жизни.
        
        \s5
        \v 22 И к одним будьте милостивы, с рассмотрением,
        \v 23 а других в страхе спасайте, выхватывая из огня, обличайте же со страхом, брезгуя даже одеждой, которая осквернена плотью.
        
        \s5
        \v 24 Тому, кто может сохранить вас от падения и поставить перед Своей славой безупречными в радости, 
        \v 25 Единому Премудрому Богу, нашему Спасителю через Иисуса Христа, нашего Господа, слава и величие, сила и власть прежде всех веков, теперь и в вечности. Аминь.
        """.trimIndent()
        return usfmBookSource.parse(usfm)
    }

    fun clearError() {
        error = null
    }

    fun onBeforeNavigate() {
        _verses.value = emptyList()
    }
}