"""Build small development packs (zh / es / ru) in the exact pack schema (ARCHITECTURE §2.4).

These are fixtures so the app runs before the real pipeline packs exist. The real
`packs/zh-hsk-1-6-v1` replaces `zh-dev`; word ids use the same scheme so progress carries over.
Level tags here are approximate; the real pipeline takes levels from the official lists.

Sources: Make Me a Hanzi (Arphic PL / LGPL), Microsoft Fluent Emoji 3D (MIT),
Microsoft neural voices via edge-tts (neural TTS, flagged human=0).

Usage: pipeline/.venv/Scripts/python.exe pipeline/tools/build_dev_pack.py
"""

from __future__ import annotations

import asyncio
import hashlib
import io
import json
import sqlite3
import unicodedata
import urllib.parse
import urllib.request
from pathlib import Path

import edge_tts
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
RAW = ROOT / "pipeline/work/raw"
OUT = ROOT / "android/app/src/main/assets/packs"
MMAH = "https://raw.githubusercontent.com/skishore/makemeahanzi/master/"
EMOJI = "https://raw.githubusercontent.com/microsoft/fluentui-emoji/main/assets/{name}/3D/{file}_3d.png"
VOICES = {
    "zh": "zh-CN-XiaoxiaoNeural", "es": "es-ES-ElviraNeural", "ru": "ru-RU-SvetlanaNeural",
    "de": "de-DE-KatjaNeural", "fr": "fr-FR-DeniseNeural", "ar": "ar-SA-ZariyahNeural",
}

# hanzi, syllables, tones, pos, gloss, measure word, sentence, sentence pinyin, translation, level, emoji
ZH = [
    ("苹果", "píng guǒ", "2 3", "n", "apple", "个", "我每天吃一个苹果。", "Wǒ měi tiān chī yí ge píngguǒ.", "I eat an apple every day.", 1, "Red apple"),
    ("猫", "māo", "1", "n", "cat", "只", "这只猫很可爱。", "Zhè zhī māo hěn kě'ài.", "This cat is very cute.", 1, "Cat"),
    ("狗", "gǒu", "3", "n", "dog", "只", "我家有一只狗。", "Wǒ jiā yǒu yì zhī gǒu.", "My family has a dog.", 1, "Dog"),
    ("水", "shuǐ", "3", "n", "water", "杯", "请给我一杯水。", "Qǐng gěi wǒ yì bēi shuǐ.", "Please give me a glass of water.", 1, "Droplet"),
    ("茶", "chá", "2", "n", "tea", "杯", "你喝茶吗？", "Nǐ hē chá ma?", "Do you drink tea?", 1, "Teacup without handle"),
    ("米饭", "mǐ fàn", "3 4", "n", "cooked rice", "碗", "我中午吃米饭。", "Wǒ zhōngwǔ chī mǐfàn.", "I eat rice at noon.", 1, "Cooked rice"),
    ("书", "shū", "1", "n", "book", "本", "这本书很好。", "Zhè běn shū hěn hǎo.", "This book is very good.", 1, "Closed book"),
    ("车", "chē", "1", "n", "car; vehicle", "辆", "他的车是红色的。", "Tā de chē shì hóngsè de.", "His car is red.", 1, "Automobile"),
    ("飞机", "fēi jī", "1 1", "n", "airplane", "架", "飞机几点起飞？", "Fēijī jǐ diǎn qǐfēi?", "What time does the plane take off?", 1, "Airplane"),
    ("电话", "diàn huà", "4 4", "n", "telephone; phone call", "个", "我给妈妈打电话。", "Wǒ gěi māma dǎ diànhuà.", "I'm calling my mom.", 1, "Telephone"),
    ("杯子", "bēi zi", "1 5", "n", "cup; glass", "个", "这个杯子是我的。", "Zhège bēizi shì wǒ de.", "This cup is mine.", 1, "Cup with straw"),
    ("椅子", "yǐ zi", "3 5", "n", "chair", "把", "请坐这把椅子。", "Qǐng zuò zhè bǎ yǐzi.", "Please sit on this chair.", 1, "Chair"),
    ("太阳", "tài yáng", "4 2", "n", "sun", "个", "今天太阳很大。", "Jīntiān tàiyáng hěn dà.", "The sun is strong today.", 1, "Sun"),
    ("月亮", "yuè liang", "4 5", "n", "moon", "个", "今天的月亮很圆。", "Jīntiān de yuèliang hěn yuán.", "The moon is very round tonight.", 1, "Crescent moon"),
    ("雨", "yǔ", "3", "n", "rain", "场", "外面下雨了。", "Wàimiàn xià yǔ le.", "It's raining outside.", 1, "Cloud with rain"),
    ("家", "jiā", "1", "n", "home; family", "个", "我想回家。", "Wǒ xiǎng huí jiā.", "I want to go home.", 1, "House"),
    ("学校", "xué xiào", "2 4", "n", "school", "所", "我们的学校很大。", "Wǒmen de xuéxiào hěn dà.", "Our school is very big.", 1, "School"),
    ("医生", "yī shēng", "1 1", "n", "doctor", "位", "他是一位医生。", "Tā shì yí wèi yīshēng.", "He is a doctor.", 1, None),
    ("朋友", "péng you", "2 5", "n", "friend", "个", "她是我的好朋友。", "Tā shì wǒ de hǎo péngyou.", "She is my good friend.", 1, None),
    ("老师", "lǎo shī", "3 1", "n", "teacher", "位", "老师好！", "Lǎoshī hǎo!", "Hello, teacher!", 1, None),
    ("衣服", "yī fu", "1 5", "n", "clothes", "件", "这件衣服很漂亮。", "Zhè jiàn yīfu hěn piàoliang.", "This piece of clothing is very pretty.", 1, "T-shirt"),
    ("钱", "qián", "2", "n", "money", None, "我没有钱了。", "Wǒ méiyǒu qián le.", "I'm out of money.", 1, "Money bag"),
    ("吃", "chī", "1", "v", "to eat", None, "你想吃什么？", "Nǐ xiǎng chī shénme?", "What do you want to eat?", 1, None),
    ("喝", "hē", "1", "v", "to drink", None, "我想喝水。", "Wǒ xiǎng hē shuǐ.", "I want to drink water.", 1, None),
    ("看", "kàn", "4", "v", "to look; to watch; to read", None, "我喜欢看书。", "Wǒ xǐhuan kàn shū.", "I like reading books.", 1, None),
    ("去", "qù", "4", "v", "to go", None, "你去哪儿？", "Nǐ qù nǎr?", "Where are you going?", 1, None),
    ("来", "lái", "2", "v", "to come", None, "他明天来北京。", "Tā míngtiān lái Běijīng.", "He's coming to Beijing tomorrow.", 1, None),
    ("学习", "xué xí", "2 2", "v", "to study; to learn", None, "我在学习中文。", "Wǒ zài xuéxí Zhōngwén.", "I'm studying Chinese.", 1, None),
    ("喜欢", "xǐ huan", "3 5", "v", "to like", None, "我喜欢猫。", "Wǒ xǐhuan māo.", "I like cats.", 1, None),
    ("说", "shuō", "1", "v", "to speak; to say", None, "你会说中文吗？", "Nǐ huì shuō Zhōngwén ma?", "Can you speak Chinese?", 1, None),
    ("大", "dà", "4", "adj", "big", None, "这个苹果很大。", "Zhège píngguǒ hěn dà.", "This apple is big.", 2, None),
    ("小", "xiǎo", "3", "adj", "small", None, "我的房间很小。", "Wǒ de fángjiān hěn xiǎo.", "My room is small.", 2, None),
    ("好", "hǎo", "3", "adj", "good; well", None, "今天天气很好。", "Jīntiān tiānqì hěn hǎo.", "The weather is nice today.", 2, None),
    ("热", "rè", "4", "adj", "hot", None, "今天很热。", "Jīntiān hěn rè.", "It's hot today.", 2, None),
    ("冷", "lěng", "3", "adj", "cold", None, "冬天很冷。", "Dōngtiān hěn lěng.", "Winter is cold.", 2, None),
    ("但是", "dàn shì", "4 4", "conj", "but; however", None, "我想去，但是没有时间。", "Wǒ xiǎng qù, dànshì méiyǒu shíjiān.", "I want to go, but I don't have time.", 2, None),
    ("因为", "yīn wèi", "1 4", "conj", "because", None, "因为下雨，我没去。", "Yīnwèi xià yǔ, wǒ méi qù.", "Because it rained, I didn't go.", 2, None),
    ("今天", "jīn tiān", "1 1", "n", "today", None, "今天是星期一。", "Jīntiān shì xīngqīyī.", "Today is Monday.", 2, None),
    ("明天", "míng tiān", "2 1", "n", "tomorrow", None, "明天见！", "Míngtiān jiàn!", "See you tomorrow!", 2, None),
    ("中文", "zhōng wén", "1 2", "n", "Chinese (language)", None, "中文很有意思。", "Zhōngwén hěn yǒu yìsi.", "Chinese is very interesting.", 2, None),
    ("你好", "nǐ hǎo", "3 3", "interj", "hello", None, "你好！我叫小明。", "Nǐ hǎo! Wǒ jiào Xiǎo Míng.", "Hello! My name is Xiao Ming.", 2, None),
    ("谢谢", "xiè xie", "4 5", "v", "thank you", None, "谢谢你的帮助。", "Xièxie nǐ de bāngzhù.", "Thank you for your help.", 2, None),
]

# headword, IPA, pos, gender/aspect, gloss, sentence, translation, emoji
ES = [
    ("la manzana", "/manˈθana/", "n", "f.", "apple", "Me como una manzana cada día.", "I eat an apple every day.", "Red apple"),
    ("el gato", "/ˈɡato/", "n", "m.", "cat", "El gato duerme en el sofá.", "The cat sleeps on the sofa.", "Cat"),
    ("el perro", "/ˈpero/", "n", "m.", "dog", "Mi perro es muy simpático.", "My dog is very friendly.", "Dog"),
    ("el agua", "/ˈaɣwa/", "n", "f.", "water", "¿Puedes darme un vaso de agua?", "Can you give me a glass of water?", "Droplet"),
    ("el libro", "/ˈliβɾo/", "n", "m.", "book", "Este libro es muy interesante.", "This book is very interesting.", "Closed book"),
    ("la casa", "/ˈkasa/", "n", "f.", "house; home", "Mi casa está cerca del parque.", "My house is near the park.", "House"),
    ("comer", "/koˈmeɾ/", "v", None, "to eat", "¿Qué quieres comer?", "What do you want to eat?", None),
    ("beber", "/beˈβeɾ/", "v", None, "to drink", "Quiero beber un café.", "I want to drink a coffee.", None),
    ("grande", "/ˈɡɾande/", "adj", None, "big", "Es una ciudad muy grande.", "It's a very big city.", None),
    ("pequeño", "/peˈkeɲo/", "adj", None, "small", "Tengo un apartamento pequeño.", "I have a small apartment.", None),
    ("hola", "/ˈola/", "interj", None, "hello; hi", "¡Hola! ¿Cómo estás?", "Hi! How are you?", None),
    ("gracias", "/ˈɡɾaθjas/", "interj", None, "thank you", "Muchas gracias por tu ayuda.", "Thank you very much for your help.", None),
    ("hoy", "/oi̯/", "adv", None, "today", "Hoy hace mucho calor.", "It's very hot today.", None),
    ("mañana", "/maˈɲana/", "adv", None, "tomorrow", "Nos vemos mañana.", "See you tomorrow.", None),
    ("el amigo", "/aˈmiɣo/", "n", "m.", "friend", "Juan es mi mejor amigo.", "Juan is my best friend.", None),
]

RU = [
    ("я́блоко", "/ˈjæbləkə/", "n", "neut.", "apple", "Я ем я́блоко ка́ждый день.", "I eat an apple every day.", "Red apple"),
    ("ко́шка", "/ˈkoʂkə/", "n", "f.", "cat", "Ко́шка спит на дива́не.", "The cat is sleeping on the sofa.", "Cat"),
    ("соба́ка", "/sɐˈbakə/", "n", "f.", "dog", "У меня́ есть соба́ка.", "I have a dog.", "Dog"),
    ("вода́", "/vɐˈda/", "n", "f.", "water", "Мо́жно стака́н воды́?", "Can I have a glass of water?", "Droplet"),
    ("кни́га", "/ˈknʲiɡə/", "n", "f.", "book", "Э́та кни́га о́чень интере́сная.", "This book is very interesting.", "Closed book"),
    ("дом", "/dom/", "n", "m.", "house; home", "Мой дом недалеко́.", "My house is not far.", "House"),
    ("есть", "/jesʲtʲ/", "v", "impf.", "to eat", "Что ты хо́чешь есть?", "What do you want to eat?", None),
    ("пить", "/pʲitʲ/", "v", "impf.", "to drink", "Я хочу́ пить.", "I'm thirsty.", None),
    ("большо́й", "/bɐlʲˈʂoj/", "adj", None, "big", "Э́то большо́й го́род.", "It's a big city.", None),
    ("ма́ленький", "/ˈmalʲɪnʲkʲɪj/", "adj", None, "small", "У меня́ ма́ленькая кварти́ра.", "I have a small apartment.", None),
    ("приве́т", "/prʲɪˈvʲet/", "interj", None, "hi", "Приве́т! Как дела́?", "Hi! How are you?", None),
    ("спаси́бо", "/spɐˈsʲibə/", "interj", None, "thank you", "Спаси́бо за по́мощь.", "Thanks for the help.", None),
    ("сего́дня", "/sʲɪˈvodʲnʲə/", "adv", None, "today", "Сего́дня о́чень жа́рко.", "It's very hot today.", None),
    ("за́втра", "/ˈzaftrə/", "adv", None, "tomorrow", "До за́втра!", "See you tomorrow!", None),
    ("друг", "/druk/", "n", "m.", "friend", "Он мой лу́чший друг.", "He is my best friend.", None),
]

# headword (with article), IPA, pos, gender, gloss, sentence, translation, emoji
DE = [
    ("der Apfel", "/ˈapfl̩/", "n", "m.", "apple", "Ich esse jeden Tag einen Apfel.", "I eat an apple every day.", "Red apple"),
    ("die Katze", "/ˈkat͡sə/", "n", "f.", "cat", "Die Katze schläft auf dem Sofa.", "The cat is sleeping on the sofa.", "Cat"),
    ("der Hund", "/hʊnt/", "n", "m.", "dog", "Mein Hund ist sehr freundlich.", "My dog is very friendly.", "Dog"),
    ("das Wasser", "/ˈvasɐ/", "n", "n.", "water", "Kann ich ein Glas Wasser haben?", "Can I have a glass of water?", "Droplet"),
    ("das Buch", "/buːx/", "n", "n.", "book", "Das Buch ist sehr interessant.", "The book is very interesting.", "Closed book"),
    ("das Haus", "/haʊ̯s/", "n", "n.", "house", "Mein Haus ist nicht weit.", "My house is not far.", "House"),
    ("die Schule", "/ˈʃuːlə/", "n", "f.", "school", "Die Schule beginnt um acht.", "School starts at eight.", "School"),
    ("essen", "/ˈɛsn̩/", "v", None, "to eat", "Was möchtest du essen?", "What would you like to eat?", None),
    ("trinken", "/ˈtrɪŋkn̩/", "v", None, "to drink", "Ich möchte einen Kaffee trinken.", "I'd like to drink a coffee.", None),
    ("groß", "/ɡroːs/", "adj", None, "big; tall", "Das ist eine große Stadt.", "That's a big city.", None),
    ("klein", "/klaɪ̯n/", "adj", None, "small", "Ich habe eine kleine Wohnung.", "I have a small flat.", None),
    ("hallo", "/haˈloː/", "interj", None, "hello", "Hallo! Wie geht's?", "Hi! How are you?", None),
    ("danke", "/ˈdaŋkə/", "interj", None, "thank you", "Danke für deine Hilfe.", "Thanks for your help.", None),
    ("heute", "/ˈhɔɪ̯tə/", "adv", None, "today", "Heute ist es sehr heiß.", "It's very hot today.", None),
    ("morgen", "/ˈmɔʁɡn̩/", "adv", None, "tomorrow", "Bis morgen!", "See you tomorrow!", None),
]

FR = [
    ("la pomme", "/pɔm/", "n", "f.", "apple", "Je mange une pomme chaque jour.", "I eat an apple every day.", "Red apple"),
    ("le chat", "/ʃa/", "n", "m.", "cat", "Le chat dort sur le canapé.", "The cat is sleeping on the sofa.", "Cat"),
    ("le chien", "/ʃjɛ̃/", "n", "m.", "dog", "Mon chien est très gentil.", "My dog is very friendly.", "Dog"),
    ("l'eau", "/o/", "n", "f.", "water", "Je peux avoir un verre d'eau ?", "Can I have a glass of water?", "Droplet"),
    ("le livre", "/livʁ/", "n", "m.", "book", "Ce livre est très intéressant.", "This book is very interesting.", "Closed book"),
    ("la maison", "/mɛ.zɔ̃/", "n", "f.", "house; home", "Ma maison n'est pas loin.", "My house isn't far.", "House"),
    ("l'école", "/e.kɔl/", "n", "f.", "school", "L'école commence à huit heures.", "School starts at eight o'clock.", "School"),
    ("manger", "/mɑ̃.ʒe/", "v", None, "to eat", "Qu'est-ce que tu veux manger ?", "What do you want to eat?", None),
    ("boire", "/bwaʁ/", "v", None, "to drink", "Je voudrais boire un café.", "I'd like to drink a coffee.", None),
    ("grand", "/ɡʁɑ̃/", "adj", None, "big; tall", "C'est une grande ville.", "It's a big city.", None),
    ("petit", "/pə.ti/", "adj", None, "small", "J'ai un petit appartement.", "I have a small flat.", None),
    ("bonjour", "/bɔ̃.ʒuʁ/", "interj", None, "hello; good morning", "Bonjour ! Comment ça va ?", "Hello! How are you?", None),
    ("merci", "/mɛʁ.si/", "interj", None, "thank you", "Merci pour ton aide.", "Thanks for your help.", None),
    ("aujourd'hui", "/o.ʒuʁ.dɥi/", "adv", None, "today", "Il fait très chaud aujourd'hui.", "It's very hot today.", None),
    ("demain", "/də.mɛ̃/", "adv", None, "tomorrow", "À demain !", "See you tomorrow!", None),
]

# Modern Standard Arabic, fully vowelled so the app can transliterate it. Verbs use the
# dictionary form (3rd person masculine past), glossed "to …".
AR = [
    ("تُفَّاحَة", "/tuf.faː.ħa/", "n", "f.", "apple", "آكُلُ تُفَّاحَةً كُلَّ يَوْمٍ.", "I eat an apple every day.", "Red apple"),
    ("قِطَّة", "/qitˤ.tˤa/", "n", "f.", "cat", "اَلْقِطَّةُ نَائِمَةٌ عَلَى الْأَرِيكَةِ.", "The cat is sleeping on the sofa.", "Cat"),
    ("كَلْب", "/kalb/", "n", "m.", "dog", "عِنْدِي كَلْبٌ.", "I have a dog.", "Dog"),
    ("مَاء", "/maːʔ/", "n", "m.", "water", "أُرِيدُ كُوبَ مَاءٍ.", "I want a glass of water.", "Droplet"),
    ("كِتَاب", "/ki.taːb/", "n", "m.", "book", "هٰذَا الْكِتَابُ مُفِيدٌ.", "This book is useful.", "Closed book"),
    ("بَيْت", "/bajt/", "n", "m.", "house; home", "بَيْتِي قَرِيبٌ.", "My house is near.", "House"),
    ("مَدْرَسَة", "/mad.ra.sa/", "n", "f.", "school", "اَلْمَدْرَسَةُ قَرِيبَةٌ مِنْ بَيْتِي.", "The school is near my house.", "School"),
    ("أَكَلَ", "/ʔa.ka.la/", "v", None, "to eat (he ate)", "مَاذَا تُرِيدُ أَنْ تَأْكُلَ؟", "What do you want to eat?", None),
    ("شَرِبَ", "/ʃa.ri.ba/", "v", None, "to drink (he drank)", "أُرِيدُ أَنْ أَشْرَبَ قَهْوَةً.", "I want to drink coffee.", None),
    ("كَبِير", "/ka.biːr/", "adj", None, "big", "هٰذِهِ مَدِينَةٌ كَبِيرَةٌ.", "This is a big city.", None),
    ("صَغِير", "/sˤa.ɣiːr/", "adj", None, "small", "عِنْدِي شَقَّةٌ صَغِيرَةٌ.", "I have a small flat.", None),
    ("مَرْحَبًا", "/mar.ħa.ban/", "interj", None, "hello", "مَرْحَبًا! كَيْفَ حَالُكَ؟", "Hello! How are you?", None),
    ("شُكْرًا", "/ʃuk.ran/", "interj", None, "thank you", "شُكْرًا عَلَى مُسَاعَدَتِكَ.", "Thank you for your help.", None),
    ("اَلْيَوْمَ", "/al.jaw.ma/", "adv", None, "today", "اَلْجَوُّ حَارٌّ جِدًّا الْيَوْمَ.", "The weather is very hot today.", None),
    ("غَدًا", "/ɣa.dan/", "adv", None, "tomorrow", "أَرَاكَ غَدًا!", "See you tomorrow!", None),
]

SCHEMA = """
CREATE TABLE pack_meta(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE word(id TEXT PRIMARY KEY, lang TEXT, headword TEXT, reading TEXT, search_key TEXT,
  level INTEGER, level_label TEXT, freq_rank INTEGER, pos TEXT, extra_json TEXT);
CREATE TABLE sense(word_id TEXT, ord INTEGER, pos TEXT, gloss TEXT);
CREATE TABLE sentence(id TEXT PRIMARY KEY, word_id TEXT, ord INTEGER, text TEXT, reading TEXT, translation TEXT,
  audio TEXT, audio_human INTEGER);
CREATE TABLE asset(word_id TEXT, kind TEXT, path TEXT, human INTEGER, source TEXT, license TEXT, attribution TEXT);
CREATE TABLE hanzi(ch TEXT PRIMARY KEY, stroke_count INTEGER, radical TEXT, strokes_json TEXT, medians_json TEXT);
CREATE INDEX word_level ON word(level, freq_rank);
CREATE INDEX word_search ON word(search_key);
CREATE INDEX sense_word ON sense(word_id, ord);
CREATE INDEX sentence_word ON sentence(word_id, ord);
CREATE INDEX asset_word ON asset(word_id);
"""

TONE_MARKS = {"a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò", "u": "ūúǔù", "ü": "ǖǘǚǜ"}


def normalize(text: str) -> str:
    """search_key contract (ARCHITECTURE §2.4), mirrored by PackReader.normalizeQuery."""
    out, base = [], " "
    for ch in unicodedata.normalize("NFD", text.lower()):
        if unicodedata.category(ch) == "Mn":
            if ch == "́" or "a" <= base <= "z" or "ً" <= ch <= "ْ" or ch == "ٰ":
                continue
        else:
            base = ch
        out.append(ch)
    return unicodedata.normalize("NFC", "".join(out))


def numbered(syllable: str, tone: int) -> str:
    plain = normalize(syllable)
    return plain + (str(tone) if tone != 5 else "5")


def fetch(url: str, dest: Path) -> Path:
    if not dest.exists():
        dest.parent.mkdir(parents=True, exist_ok=True)
        print("downloading", url)
        urllib.request.urlretrieve(url, dest)
    return dest


def load_strokes(chars: set[str]) -> dict[str, dict]:
    graphics = fetch(MMAH + "graphics.txt", RAW / "makemeahanzi/graphics.txt")
    dictionary = fetch(MMAH + "dictionary.txt", RAW / "makemeahanzi/dictionary.txt")
    radicals = {}
    for line in dictionary.read_text(encoding="utf-8").splitlines():
        d = json.loads(line)
        if d["character"] in chars:
            radicals[d["character"]] = d.get("radical")
    out = {}
    for line in graphics.read_text(encoding="utf-8").splitlines():
        g = json.loads(line)
        if g["character"] in chars:
            out[g["character"]] = {"strokes": g["strokes"], "medians": g["medians"], "radical": radicals.get(g["character"])}
    return out


def sticker(png: bytes) -> bytes:
    """Fluent 3D emoji -> die-cut sticker (white outline + soft shadow) as WebP (DESIGN-SYSTEM §5.4)."""
    img = Image.open(io.BytesIO(png)).convert("RGBA").resize((220, 220), Image.LANCZOS)
    canvas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    canvas.paste(img, (18, 18), img)
    alpha = canvas.getchannel("A")
    outline = alpha.filter(ImageFilter.MaxFilter(13))
    shadow = outline.filter(ImageFilter.GaussianBlur(6)).point(lambda a: int(a * 0.25))
    result = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    result.paste((0, 0, 0, 255), (0, 6), shadow)
    result.paste((255, 255, 255, 255), (0, 0), outline)
    result.alpha_composite(canvas)
    buf = io.BytesIO()
    result.save(buf, "WEBP", quality=82, method=6)
    return buf.getvalue()


def emoji_webp(name: str) -> bytes | None:
    file = name.lower().replace(" ", "_")
    cache = RAW / "fluent" / f"{file}.png"
    if not cache.exists():
        url = EMOJI.format(name=urllib.parse.quote(name), file=urllib.parse.quote(file))
        try:
            fetch(url, cache)
        except Exception as e:  # noqa: BLE001 - missing emoji is fine, the card falls back to typographic art
            print("  no emoji for", name, e)
            return None
    return sticker(cache.read_bytes())


async def tts(text: str, voice: str, dest: Path) -> None:
    if dest.exists():
        return
    dest.parent.mkdir(parents=True, exist_ok=True)
    await edge_tts.Communicate(text, voice).save(str(dest))


def write_pack(pack_id: str, lang: str, title: str, rows: list[dict], strokes: dict[str, dict] | None) -> None:
    folder = OUT / pack_id
    (folder / "audio").mkdir(parents=True, exist_ok=True)
    (folder / "img").mkdir(parents=True, exist_ok=True)
    db_path = folder / "pack.db"
    db_path.unlink(missing_ok=True)
    con = sqlite3.connect(db_path)
    con.executescript(SCHEMA)
    con.executemany("INSERT INTO pack_meta VALUES (?, ?)", [("id", pack_id), ("lang", lang), ("version", "1"), ("schema_version", "1"), ("title", title)])
    jobs = []
    for i, r in enumerate(rows):
        con.execute("INSERT INTO word VALUES (?,?,?,?,?,?,?,?,?,?)", (r["id"], lang, r["headword"], r["reading"], r["search_key"], r["level"], r["level_label"], i + 1, r["pos"], json.dumps(r["extra"], ensure_ascii=False)))
        con.execute("INSERT INTO sense VALUES (?,?,?,?)", (r["id"], 0, r["pos"], r["gloss"]))
        word_audio = f"audio/w{i + 1:04d}.mp3"
        sent_audio = f"audio/s{i + 1:04d}.mp3"
        jobs.append(tts(r["tts_word"], VOICES[lang], folder / word_audio))
        jobs.append(tts(r["tts_sentence"], VOICES[lang], folder / sent_audio))
        con.execute("INSERT INTO sentence VALUES (?,?,?,?,?,?,?,?)", (f"{r['id']}#s0", r["id"], 0, r["sentence"], r.get("sentence_reading"), r["translation"], sent_audio, 0))
        con.execute("INSERT INTO asset VALUES (?,?,?,?,?,?,?)", (r["id"], "word_audio", word_audio, 0, f"edge-tts:{VOICES[lang]}", "neural TTS", "Microsoft neural voice"))
        if r.get("emoji"):
            webp = emoji_webp(r["emoji"])
            if webp:
                path = f"img/i{i + 1:04d}.webp"
                (folder / path).write_bytes(webp)
                con.execute("INSERT INTO asset VALUES (?,?,?,?,?,?,?)", (r["id"], "image", path, 0, "Microsoft Fluent Emoji 3D", "MIT", r["emoji"]))
    if strokes:
        for ch, s in strokes.items():
            con.execute("INSERT INTO hanzi VALUES (?,?,?,?,?)", (ch, len(s["strokes"]), s["radical"], json.dumps(s["strokes"]), json.dumps(s["medians"])))
    con.commit()
    con.close()

    async def run_all():
        sem = asyncio.Semaphore(4)

        async def guarded(job):
            async with sem:
                await job

        await asyncio.gather(*(guarded(j) for j in jobs))

    asyncio.run(run_all())
    files = sorted(p for p in folder.rglob("*") if p.is_file() and p.name != "manifest.json")
    manifest = {
        "id": pack_id, "lang": lang, "version": 1, "title": title, "dev_fixture": True,
        "files": {str(p.relative_to(folder)).replace("\\", "/"): hashlib.sha256(p.read_bytes()).hexdigest() for p in files},
    }
    (folder / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{pack_id}: {len(rows)} words, {sum(1 for p in files if p.suffix == '.webp')} images, {sum(1 for p in files if p.suffix == '.mp3')} audio files")


def main() -> None:
    chars = {ch for row in ZH for ch in row[0]}
    strokes = load_strokes(chars)
    missing = chars - strokes.keys()
    assert not missing, f"no stroke data for {missing}"

    zh_rows = []
    for hz, syl, tones, pos, gloss, mw, sent, sent_py, tr, level, emoji in ZH:
        syllables = syl.split()
        tone_list = [int(t) for t in tones.split()]
        num = "".join(numbered(s, t) for s, t in zip(syllables, tone_list))
        extra = {"syllables": syllables, "tones": tone_list}
        if mw:
            extra["measure_word"] = mw
        zh_rows.append({
            "id": f"zh:{hz}:{num}:{pos}", "headword": hz, "reading": "".join(syllables), "pos": pos, "gloss": gloss,
            "level": level, "level_label": f"HSK {level}", "extra": extra,
            "search_key": " ".join([hz, normalize("".join(syllables)), normalize(" ".join(syllables)), num, normalize(gloss)]),
            "sentence": sent, "sentence_reading": sent_py, "translation": tr, "emoji": emoji,
            "tts_word": hz, "tts_sentence": sent,
        })

    def cefr_rows(lang: str, data: list[tuple]) -> list[dict]:
        rows = []
        for head, ipa, pos, gender, gloss, sent, tr, emoji in data:
            plain = head.replace("́", "")
            lemma = plain
            for article in ("el ", "la ", "der ", "die ", "das ", "le ", "l'"):
                if plain.startswith(article):
                    lemma = plain[len(article):]
                    break
            lemma = "".join(ch for ch in lemma if not ("ً" <= ch <= "ْ" or ch == "ٰ"))
            extra = {"gender": gender} if gender else {}
            rows.append({
                "id": f"{lang}:{lemma}:{pos}", "headword": head, "reading": ipa, "pos": pos, "gloss": gloss,
                "level": 1, "level_label": "A1", "extra": extra,
                "search_key": " ".join([normalize(head), normalize(gloss)]),
                "sentence": sent, "translation": tr, "emoji": emoji,
                "tts_word": plain, "tts_sentence": sent.replace("́", ""),
            })
        return rows

    write_pack("zh-dev", "zh", "Mandarin (dev fixture)", zh_rows, strokes)
    write_pack("es-dev", "es", "Spanish (dev fixture)", cefr_rows("es", ES), None)
    write_pack("ru-dev", "ru", "Russian (dev fixture)", cefr_rows("ru", RU), None)
    write_pack("de-dev", "de", "German (dev fixture)", cefr_rows("de", DE), None)
    write_pack("fr-dev", "fr", "French (dev fixture)", cefr_rows("fr", FR), None)
    write_pack("ar-dev", "ar", "Arabic (dev fixture)", cefr_rows("ar", AR), None)


if __name__ == "__main__":
    main()
