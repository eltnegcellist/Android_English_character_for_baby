package validation.tts

import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class JapaneseRomajiName(val text: String)
data class NativeWord(val text: String,val ipa: String)
data class NativeAnalysis(val ipa: String,val words: List<NativeWord>)

// Independent, bounded CMU alternative. Not a port of Misaki's POS model.
// Only the pinned fixed Lite catalogue plus an explicitly tagged name prefix
// can be rendered. Full and arbitrary new sentences fail before inference.
class NativeLitePlanner(dictionary: File, allowedTexts: Collection<String>, private val lengthVariant: Boolean=false, private val compoundVariant: Boolean=false) : Planner {
    companion object {
        const val DICTIONARY_SHA="81917843c7f44ce2b094ac63873c2c7a4cf802040792c455ba3ca406891c3d22"
        private val arpabet=mapOf(
            "AA" to "ɑ","AE" to "æ","AH" to "ʌ","AO" to "ɔ","AW" to "aʊ","AY" to "aɪ",
            "B" to "b","CH" to "tʃ","D" to "d","DH" to "ð","EH" to "ɛ","ER" to "ɜɹ",
            "EY" to "eɪ","F" to "f","G" to "ɡ","HH" to "h","IH" to "ɪ","IY" to "i",
            "JH" to "dʒ","K" to "k","L" to "l","M" to "m","N" to "n","NG" to "ŋ",
            "OW" to "oʊ","OY" to "ɔɪ","P" to "p","R" to "ɹ","S" to "s","SH" to "ʃ",
            "T" to "t","TH" to "θ","UH" to "ʊ","UW" to "u","V" to "v","W" to "w",
            "Y" to "j","Z" to "z","ZH" to "ʒ")
        private val longPhones=setOf("AA","AO","IY","UW")
        private val own=mapOf("peekaboo" to "pˈikəbˌu","pitter" to "pˈɪtəɹ","mmm" to "mː")
        private val token=Regex("[A-Za-z]+(?:'[A-Za-z]+)?(?:-[A-Za-z]+(?:'[A-Za-z]+)?)*|[.!?,;:]")
        private fun normalize(text: String)=text.trim().replace(Regex("\\s+")," ").lowercase(Locale.ROOT)
        fun sha(file: File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {"%02x".format(it)}
    }
    private val catalogue=allowedTexts.map(::normalize).toSet()
    private val pronunciations=mutableMapOf<String,String>()
    init {
        require(sha(dictionary)==DICTIONARY_SHA) {"CMU checksum mismatch"}
        require(allowedTexts.size==202) {"Only the pinned 202-entry Lite catalogue is supported"}
        val catalogueHash=MessageDigest.getInstance("SHA-256").digest(allowedTexts.joinToString("\n",postfix="\n").toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
        require(catalogueHash=="f42ad1899cdb637e219b5fbc4705e8e16842e523c60fac740ef8eb0a8afe9517") {"Unqualified Lite catalogue"}
        dictionary.forEachLine {line ->
            val parts=line.substringBefore('#').trim().split(Regex("\\s+"))
            if(parts.size>=2) pronunciations[parts.first()]=parts.drop(1).joinToString("") {phone ->
                val stress=phone.lastOrNull()?.takeIf {it in '0'..'2'}
                val base=if(stress!=null) phone.dropLast(1) else phone
                var sound=if(base=="AH" && stress=='0') "ə" else if(base=="ER" && stress=='0') "əɹ" else arpabet[base] ?: error("Unknown ARPABET: $phone")
                // Explicit acoustic experiment; not asserted to be CMU length data.
                if(lengthVariant && base in longPhones) sound+="ː"
                (if(stress=='1') "ˈ" else if(stress=='2') "ˌ" else "")+sound
            }
        }
    }
    fun analyze(text: String,name: JapaneseRomajiName?=null): NativeAnalysis {
        require(text.none {it in "[]\n\r\t"}) {"Embedded phoneme/control syntax rejected"}
        val cleaned=text.trim().replace('’','\'')
        var body=cleaned;var nameIpa: String?=null;var prefix=""
        if(name!=null) {
            nameIpa=romaji(name.text)
            val match=Regex("^"+Regex.escape(name.text)+"([,!])\\s+",RegexOption.IGNORE_CASE).find(body)
            require(match!=null) {"Name must be a separate catalogue prefix"}
            prefix=nameIpa+match.groupValues[1]+" ";body=body.substring(match.range.last+1)
        }
        require(normalize(body) in catalogue) {"Outside qualified fixed Lite domain; no guessing fallback"}
        val words=mutableListOf<NativeWord>();val output=StringBuilder(prefix);var previous=0
        if(nameIpa!=null) words.add(NativeWord(name!!.text,nameIpa))
        for(match in token.findAll(body)) {
            val gap=body.substring(previous,match.range.first)
            require(gap.all {it.isWhitespace()}) {"Unsupported character"}
            output.append(gap)
            val textWord=match.value
            if(textWord.length==1 && textWord[0] in ".!?,;:") output.append(textWord)
            else {
                val ipa=word(textWord.lowercase(Locale.ROOT),body)
                words.add(NativeWord(textWord,ipa));output.append(ipa)
            }
            previous=match.range.last+1
        }
        require(body.substring(previous).all {it.isWhitespace()}) {"Unparsed text"}
        return NativeAnalysis(output.toString().trim(),words)
    }
    private fun word(word: String,context: String): String {
        // Separately evaluated American compound stress/allophony experiment.
        // Initial primary and later secondary stress independently confirmed:
        // https://dictionary.cambridge.org/us/pronunciation/english/pitter-patter
        if(word=="pitter-patter" && compoundVariant) return "pˈɪɾəɹpˌæɾəɹ"
        if('-' in word) {
            val parts=word.split('-').map {word(it,context)}
            // A single compound primary; preserve each component's segments.
            return parts.dropLast(1).joinToString("") {it.replace('ˈ','ˌ')}+parts.last()
        }
        own[word]?.let {return it}
        val key=when(word) {
            "read" -> {require(Regex("\\blet's\\s+read\\b",RegexOption.IGNORE_CASE).containsMatchIn(context));"read(2)"}
            "close" -> if(Regex("\\bopen\\s*(?:,|and)\\s+close\\s*[.!?]",RegexOption.IGNORE_CASE).containsMatchIn(context)) "close(2)" else "close"
            else -> word
        }
        var ipa=pronunciations[key] ?: error("Unresolved word: $word")
        if(word=="outside") {
            val first=ipa.indexOf('ˈ');val last=ipa.lastIndexOf('ˈ')
            if(first!=last) {
                val index=if(Regex("\\boutside\\s+time\\b",RegexOption.IGNORE_CASE).containsMatchIn(context)) last else first
                ipa=ipa.substring(0,index)+"ˌ"+ipa.substring(index+1)
            }
        }
        return ipa
    }
    override fun plan(text: String)=planNamed(text,null)
    fun planNamed(text: String,name: JapaneseRomajiName?): List<String> {
        val ipa=analyze(text,name).ipa
        val chunks=mutableListOf<String>()
        for(sentence in ipa.split(Regex("(?<=[.!?])\\s+"))) {
            val current=StringBuilder();var cost=0
            for(char in sentence.trim()) {
                val size=if(char=='.') 2 else 1
                if(cost+size+3>400) {chunks.add(current.toString());current.clear();cost=0}
                current.append(char);cost+=size
            }
            if(current.isNotEmpty()) chunks.add(current.toString())
        }
        require(chunks.isNotEmpty());return chunks
    }
    private fun romaji(name: String): String {
        val normalized=name.lowercase(Locale.ROOT).trim()
        val suffix=Regex("(?:-|\\s)chan$").find(normalized)
        val base=if(suffix==null) normalized else normalized.substring(0,suffix.range.first)
        require(base.matches(Regex("[a-z]+(?:[ -][a-z]+)*"))) {"Unsupported name spelling"}
        val clusters=linkedMapOf("shi" to "ʃi","chi" to "tʃi","tsu" to "tsu","fu" to "fu","ji" to "dʒi","sha" to "ʃɑ","shu" to "ʃu","sho" to "ʃo","cha" to "tʃɑ","chu" to "tʃu","cho" to "tʃo","ja" to "dʒɑ","ju" to "dʒu","jo" to "dʒo")
        val vowels=mapOf('a' to "ɑ",'i' to "i",'u' to "u",'e' to "e",'o' to "o")
        val consonants=mapOf('k' to "k",'s' to "s",'t' to "t",'n' to "n",'h' to "h",'m' to "m",'r' to "ɹ",'y' to "j",'w' to "w",'g' to "ɡ",'z' to "z",'d' to "d",'b' to "b",'p' to "p",'f' to "f",'v' to "v")
        val parts=base.split(Regex("[ -]")).map {part ->
            val units=mutableListOf<String>();var i=0
            while(i<part.length) {
                val cluster=clusters.keys.firstOrNull {part.startsWith(it,i)}
                val c=part[i]
                when {
                    cluster!=null -> {units.add(clusters.getValue(cluster));i+=cluster.length}
                    c in vowels -> {units.add(vowels.getValue(c));i++}
                    c=='n' && (i+1==part.length || part[i+1] !in "aeiouy") -> {units.add("n");i++}
                    c in consonants && i+1<part.length && part[i+1]==c -> {units.add(consonants.getValue(c));i++}
                    c in consonants && i+1<part.length && part[i+1] in vowels -> {units.add(consonants.getValue(c)+vowels.getValue(part[i+1]));i+=2}
                    c in consonants && i+2<part.length && part[i+1]=='y' && part[i+2] in "auo" -> {units.add(consonants.getValue(c)+"j"+vowels.getValue(part[i+2]));i+=3}
                    else -> error("Unsupported romaji sequence: ${part.substring(i)}")
                }
            }
            require(units.isNotEmpty());val first=units.first();val vowel=first.indexOfFirst {it in "ɑiueo"}
            if(vowel>=0) units[0]=first.substring(0,vowel)+"ˈ"+first.substring(vowel)
            units.joinToString("")
        }
        return parts.joinToString(" ")+if(suffix==null) "" else " tʃɑn"
    }
}
